# Benchmark Synthesis: Single Universe Lookup

## Environment

| Parameter | Value |
|---|---|
| JMH version | 1.37 |
| JVM | JDK 17.0.18, OpenJDK 64-Bit Server VM (17.0.18+8) |
| Benchmark mode | Average time (ns/op) |
| Warmup | 5 iterations × 1 s |
| Measurement | 5 iterations × 1 s |
| Forks | 2 |
| Total samples | 10 per configuration |
| VM options | `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` |
| Blackhole mode | Compiler (auto-detected) |

The `iterations` parameter is pinned to 100 but unused by the three benchmarked methods — each call constructs or returns the enum universe exactly once.

---

## Results

### Latency (ns/op, average time)

| enumSize | sharedSecrets | cached | direct |
|---:|---:|---:|---:|
| 4 | 0.466 ± 0.005 | 1.268 ± 0.010 | 2.457 ± 0.031 |
| 16 | 0.474 ± 0.009 | 1.264 ± 0.009 | 4.529 ± 0.026 |
| 64 | 0.465 ± 0.005 | 1.269 ± 0.011 | 15.283 ± 0.052 |
| 256 | 0.462 ± 0.006 | 1.266 ± 0.011 | 58.983 ± 0.689 |

### GC Allocation (alloc/op)

| enumSize | sharedSecrets | cached | direct |
|---:|---:|---:|---:|
| 4 | ≈ 10⁻⁶ B/op | ≈ 10⁻⁵ B/op | 32 B/op |
| 16 | ≈ 10⁻⁶ B/op | ≈ 10⁻⁵ B/op | 80 B/op |
| 64 | ≈ 10⁻⁶ B/op | ≈ 10⁻⁵ B/op | 272 B/op |
| 256 | ≈ 10⁻⁶ B/op | ≈ 10⁻⁵ B/op | 1040 B/op |

`sharedSecrets` and `cached` allocate nothing observable per call (GC count ≈ 0 for all sizes). `direct` triggers continuous GC activity (~23 collections per second per fork) regardless of enum size; the per-call allocation grows linearly with enumSize: `(enumSize + 2) * 4` bytes (object header + array header + references), matching a defensive `Arrays.copyOf` of the internal reference array.

---

## Ratios

| enumSize | cached / sharedSecrets | direct / cached | direct / sharedSecrets |
|---:|---:|---:|---:|
| 4 | 2.72× | 1.94× | 5.27× |
| 16 | 2.67× | 3.58× | 9.56× |
| 64 | 2.73× | 12.04× | 32.87× |
| 256 | 2.74× | 46.59× | 127.67× |

---

## Analysis

**`cached` vs `sharedSecrets` — constant overhead.**
The `cached / sharedSecrets` ratio is stable across all enum sizes: 2.67–2.74×. The absolute gap is also constant at ~0.80 ns, reflecting the fixed cost of a `ClassValue` lookup (hash map probe + identity comparison on the class object) with no dependence on the number of enum constants. From a library perspective, this ~0.8 ns penalty is the unavoidable price for avoiding the JDK-internal `SharedSecrets` API.

**`direct` — cost grows linearly with enumSize.**
`Class.getEnumConstants()` makes a defensive copy of the internal array on every call. The copy dominates: latency scales essentially linearly with enumSize (2.5 → 4.5 → 15.3 → 59.0 ns tracks closely with 4 → 16 → 64 → 256 elements). At enumSize 256 the copy costs ~57 ns on top of the ~1 ns base overhead, and allocates 1040 B per call.

**`direct` vs `cached` gap widens dramatically.**
At enumSize 4 the penalty of using `direct` over `cached` is under 2×. By enumSize 256 it is nearly 47×. For any code path that accesses the enum universe in a hot loop, even moderate enum sizes make the copy cost prohibitive.

**`sharedSecrets` is size-independent.**
The internal zero-copy path returns the live backing array without any allocation or copy. Its latency (0.46–0.47 ns) and allocation (≈ 10⁻⁶ B/op, noise floor) are flat across the full size range, confirming it is bounded only by the cost of a single field dereference. It is inaccessible to library code without `--add-exports`, so it serves purely as a theoretical floor.

**Practical recommendation.**
`cached` (ClassValue lookup) captures nearly all of the performance advantage of `sharedSecrets` — within ~0.8 ns across every enum size — while remaining zero-allocation and zero-copy. It is the correct implementation for library code that needs repeated access to the enum universe.
