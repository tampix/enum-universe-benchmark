# Enum Universe Benchmark — Stack Profiler Synthesis

## Environment

| Property | Value |
|---|---|
| JMH version | 1.37 |
| JVM | OpenJDK 64-Bit Server VM, JDK 17.0.18+8 |
| Benchmark mode | Average time (ns/op) |
| Warmup | 5 iterations × 1 s |
| Measurement | 10 iterations × 1 s |
| Threads | 1 |
| Fork | 1 |
| Blackhole mode | Compiler (auto-detected) |
| Parameters | enumSize=64, iterations=10000 |

---

## Timing Results

| Benchmark | Mean (ns/op) | ± Error (99.9% CI) | Stddev | Min | Max |
|---|---|---|---|---|---|
| cachedHotLoop | 13 198.774 | ± 323.681 | 214.095 | 12 926.752 | 13 497.801 |
| directHotLoop | 149 192.005 | ± 1 379.918 | 912.730 | 148 120.060 | 150 611.612 |

### Speedup and per-iteration cost

- Speedup: `directHotLoop / cachedHotLoop` = **~11.3×** slower for the direct path.
- Per-iteration cost (÷ 10 000 iterations):
  - cachedHotLoop: **~1.32 ns / iteration**
  - directHotLoop: **~14.92 ns / iteration**

---

## Thread State Distribution

Both benchmarks show an identical split across all profiler samples:

| State | Share |
|---|---|
| RUNNABLE | 66.7% |
| TIMED_WAITING | 33.3% |

The TIMED_WAITING portion is entirely `java.lang.Object.wait`, which is the JMH infrastructure thread sleeping between iterations. It carries no benchmark work and can be ignored.

---

## RUNNABLE Stack Frame Breakdown

Within the RUNNABLE pool, approximately half the samples show an empty stack (filtered by the profiler) and half land on the benchmark method itself.

### cachedHotLoop

| Share of all samples | Share of RUNNABLE | Frame |
|---|---|---|
| 33.3% | 50.0% | `<stack is empty, everything is filtered?>` |
| 33.2% | 49.8% | `bench.EnumUniverseBenchmark.cachedHotLoop` |
| 0.0% | 0.1% | `java.util.concurrent.ConcurrentHashMap.putVal` |
| 0.0% | 0.1% | JMH harness frames (init, call stub) |

### directHotLoop

| Share of all samples | Share of RUNNABLE | Frame |
|---|---|---|
| 33.3% | 50.0% | `<stack is empty, everything is filtered?>` |
| 33.0% | 49.4% | `bench.EnumUniverseBenchmark.directHotLoop` |
| 0.2% | 0.4% | `java.lang.Class.getEnumConstants` |
| 0.0% | 0.1% | `jdk.internal.misc.Unsafe.getIntVolatile` |
| 0.0% | 0.1% | JMH harness frames (call stub, announceWarmupReady) |

---

## Interpretation

### cachedHotLoop

The profiler sees no callee frames inside the benchmark method. The 0.1% hit on `ConcurrentHashMap.putVal` is a negligible one-off from JMH bookkeeping. The absence of any dispatch chain shows the JIT has fully inlined the `ClassValue` cache lookup into the loop body: samples land on the outermost benchmark frame because the native code sequence maps back to that boundary. At ~1.3 ns/iteration the cost is consistent with a single cached array reference load and a trivial conditional, with no allocation on the hot path.

### directHotLoop

`java.lang.Class.getEnumConstants` appears at 0.4% of RUNNABLE samples. Given the low total sample count this is a meaningful signal: the method is not fully inlined by the JIT. `getEnumConstants` performs a defensive clone of the internal enum constant array on every call to prevent callers from mutating the cached copy; the accompanying `Unsafe.getIntVolatile` sample is consistent with the volatile read inside that reflective path. The 11.3× slowdown relative to cachedHotLoop is largely attributable to this per-call clone — heap allocation plus copy for a 64-element array on each of 10 000 iterations — rather than to complex control flow. The JIT cannot eliminate the allocation because `getEnumConstants` returns across a public API boundary.

### Empty-stack samples

The ~50% empty-stack share within RUNNABLE is a known stack-profiler artifact. When the sampled thread is executing a fully compiled, register-intensive native sequence, the Java stack frame is absent or filtered, inflating the empty-stack bucket. It does not indicate idle time.
