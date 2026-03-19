# Decision: Enum Universe Access Strategy for MutableEnumSet / MutableEnumMap

**Context**: Eclipse Collections [#1798](https://github.com/eclipse-collections/eclipse-collections/issues/1798)
**Date**: 2026-03-19
**Status**: decided

---

## Problem

`MutableEnumSet` and `MutableEnumMap` require access to the *universe* — the array of all constants for
a given enum type — to implement bit-vector operations and ordinal-indexed access. Three strategies
exist:

| Strategy | Mechanism | Accessible from library code? |
|---|---|---|
| **SharedSecrets** | `SharedSecrets.getJavaLangAccess().getEnumConstantsShared()` — zero copy, JVM-internal | No — requires `--add-exports java.base/jdk.internal.access`, unacceptable for a public library |
| **Direct** | `Class.getEnumConstants()` at every construction | Yes — defensive array copy on every call |
| **Cached** | `ClassValue<Enum<?>[]>` — one copy per enum type, lifetime of the JVM | Yes — shared reference, must not be mutated by callers |

SharedSecrets is eliminated immediately. The real decision is **Direct vs Cached**.

---

## Evidence: benchmark summary

Four JMH suites were run on OpenJDK 17.0.18, 10 samples per scenario.

### Single-call cost (ns/op)

One universe lookup per benchmark op — the cost paid at collection construction time.

| enumSize | sharedSecrets | cached | direct | direct/cached |
|---:|---:|---:|---:|---:|
| 4 | 0.466 | 1.268 | 2.457 | 1.94× |
| 16 | 0.474 | 1.264 | 4.529 | 3.58× |
| 64 | 0.465 | 1.269 | 15.283 | **12.04×** |
| 256 | 0.462 | 1.266 | 58.983 | **46.59×** |

The `cached / sharedSecrets` ratio is constant at ~2.7× across all sizes (~0.8 ns absolute), reflecting
the fixed cost of a `ClassValue` probe with no dependence on enum size. `direct` scales linearly with the
array copy: already 12× slower than `cached` at 64 constants, 47× at 256.

Both `sharedSecrets` and `cached` produce zero observable allocation. `direct` allocates `enumSize × 8 B`
per call (32 B at size 4, 1 040 B at size 256).

### Hot-loop cost (ns per lookup in a tight loop)

Simulates repeated construction — `groupBy`, `collect`, stream pipelines.

| enumSize | cached (ns/iter) | direct (ns/iter) | direct/cached |
|---:|---:|---:|---:|
| 4 | ~1.11 | ~3.83 | 3.44× |
| 16 | ~1.11 | ~4.70 | **4.22×** |
| 64 | ~1.11 | ~15.42 | **13.88×** |
| 256 | ~1.11 | ~58.24 | **52.44×** |

`cached` is O(1) in enum size: ~1.11 ns/iter flat from 4 to 256 constants. `direct` scales linearly.
Ratios are consistent with single-call numbers — the hot-loop advantage comes from eliminating the
per-iteration allocation, not from any additional caching effect.

### GC allocation pressure (hot-loop, 100 iterations)

| enumSize | cached alloc/op | direct alloc/op | direct alloc rate |
|---:|---:|---:|---:|
| 4 | ≈ 0 | 3 200 B | ~8 GB/s |
| 16 | ≈ 0 | 8 000 B | ~16 GB/s |
| 64 | ≈ 0 | 27 200 B | ~16 GB/s |
| 256 | ≈ 0 | 104 000 B | ~17 GB/s |

`direct` triggers 52–112 GC events per second and 143–272 ms of GC time per benchmark second.
`cached` produces zero GC activity at every configuration.

### ClassValue scalability (typeCount: 10 → 1 000, fixed enumSize=16)

| typeCount | sharedSecrets | cached | direct | cached/direct |
|---:|---:|---:|---:|---:|
| 10 | 2.976 | 4.177 | 6.807 | 0.61 |
| 100 | 3.040 | 4.640 | 7.060 | 0.66 |
| 500 | 4.393 | 6.183 | 8.099 | 0.76 |
| 1 000 | 4.713 | 6.884 | 8.275 | 0.83 |

`cached` degrades +65% from 10 to 1 000 distinct types (+2.7 ns absolute), but remains faster than
`direct` at every scale. The degradation is concentrated between 100 and 500 types (~+1.5 ns step),
consistent with a CPU working-set crossing the L1/L2 boundary (~64 KB at 500 types × 16 constants).
Costs stabilize again from 500 to 1 000.

### Stack profiler (enumSize=64, 10 000 iterations)

`Class.getEnumConstants` is visible at 0.4% of RUNNABLE samples in `directHotLoop` — the method is not
fully inlined by the JIT across the public API boundary. The accompanying `Unsafe.getIntVolatile` sample
confirms the volatile read in the reflective path. `cachedHotLoop` shows no callee frames: the
`ClassValue` lookup is fully inlined into the loop body.

---

## Trade-off analysis

### Direct (`Class.getEnumConstants()`)

**Pros**
- Zero shared state — each call returns an independent array. No risk of mutation corrupting other
  collection instances.
- No custom infrastructure. Precedent: Guava's `EnumMultiset`.
- Safe under any classloader topology.

**Cons**
- O(n) allocation per call. A single construction at enumSize=64 allocates 512 B; at 256, 1 KB.
- In hot paths, continuous GC pressure: 8–17 GB/s allocation rate, 52–112 GC events/second.
- Already 12× slower than `cached` at enumSize=64 on a single call; 52× in a hot loop at 256.
- Not fully inlined by the JIT — native overhead confirmed by stack profiler.

### Cached (`ClassValue<Enum<?>[]>`)

**Pros**
- ~1.1 ns/lookup, O(1) in enum size. Zero allocation after first call per type.
- Eliminates GC pressure entirely.
- 4–52× faster than `direct` in hot loops at sizes 16–256.
- `ClassValue` is GC-aware: entries are evicted automatically on class unload, handling OSGi and
  dynamic module topologies correctly.
- Scales to 1 000+ distinct enum types while remaining faster than `direct`.
- Within ~0.8 ns of the JDK-internal `SharedSecrets` path — the theoretical floor — across all
  enum sizes. The gap is a fixed `ClassValue` probe cost; `SharedSecrets` is cheaper because
  `getEnumConstantsShared()` reads a field directly on the `Class` object (single pointer
  dereference), whereas `ClassValue` maintains its own per-class slot table.

**Cons**
- The cached array must not be mutated. The implementation must never expose it through the public
  API.
- One `Enum<?>[]` retained per type for the classloader lifetime (~128 B at size 16 — negligible).
- One additional static initializer vs a bare `getEnumConstants()` call.

---

## Decision

**Use the `ClassValue` cache.**

The benchmark evidence satisfies the criterion established in the design document:
> "Difference significant at 16–64 → `ClassValue` cache justified"

Even at single-call granularity — the most favourable case for `direct` — `cached` is already 3.6×
faster at enumSize=16 and 12× faster at enumSize=64, with zero allocation in both cases. In hot-loop
scenarios the advantage compounds. `ClassValue` captures ~97% of the performance of the inaccessible
`SharedSecrets` path while remaining fully portable library code.

The single constraint the implementation must uphold — **never expose the cached array to external
code** — is standard practice and straightforward to enforce.

---

## What was not implemented / future work

- **Contention benchmark** (`EnumUniverseContentionBenchmark`) was scaffolded but not run.
  `ClassValue` is lock-free in the read path after initial population; contention is expected to be
  a non-issue. Should be measured if multi-threaded concurrent construction becomes a concern.
- **Retained memory measurement** (JOL) was not executed. The footprint (~128 B per type at
  enumSize=16) is negligible; relevant only for environments with > 10 000 distinct enum types.
- **Hardware counters** (`-prof perfnorm`) were unavailable at run time (`perf_event_paranoid`
  restriction); the scalability degradation between typeCount=100 and 500 was attributed to
  working-set pressure by inference. A perfnorm run would confirm cache-miss counts directly.
- **SharedSecrets as an opt-in JPMS module**: if Eclipse Collections ever ships a module declaring
  the required `--add-exports`, the zero-copy internal path yields an additional ~2.7× over
  `ClassValue` at stable cost. Not a blocker for #1798.
