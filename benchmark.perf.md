# Benchmark Synthesis: EnumUniverseCacheScalabilityBenchmark

## Environment

| Parameter | Value |
|---|---|
| JMH version | 1.37 |
| JVM | OpenJDK 64-Bit Server VM, JDK 17.0.18+8 |
| VM options | `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` |
| Mode | Average time (ns/op), lower is better |
| Warmup | 5 iterations × 1 s |
| Measurement | 5 iterations × 1 s |
| Forks | 2 (10 total samples per scenario) |
| Threads | 1 |
| Profiler | stack (sampling) |
| Blackhole | compiler (auto-detected) |
| Total run time | 4 min 05 s |

No perfnorm data is present; hardware counter metrics (cache-misses/op, instructions/op, CPI) are not available.

## Results

All measurements in **ns/op**. Error is ±99.9% CI. Source: final `Result` lines from JMH output.

| typeCount | sharedSecretsRandomType | cachedLookupRandomType | directRandomType |
|---|---|---|---|
| 10 | 2.976 ± 0.155 | 4.177 ± 0.171 | 6.807 ± 0.136 |
| 100 | 3.040 ± 0.140 | 4.640 ± 0.128 | 7.060 ± 0.181 |
| 500 | 4.393 ± 0.125 | 6.183 ± 0.270 | 8.099 ± 0.178 |
| 1000 | 4.713 ± 0.113 | 6.884 ± 0.224 | 8.275 ± 0.064 |

## Stack Profiler

Thread state distribution is identical across all strategies and typeCount values:

- ~66.7% RUNNABLE, ~33.3% TIMED_WAITING (the waiting thread is the JMH control thread, `java.lang.Object.wait`)

Within RUNNABLE, ~50% of samples fall into the inlined benchmark stub (filtered by the profiler), and ~50% show `<stack is empty, everything is filtered?>`, indicating the hot path is fully inlined by the JIT. No meaningful frames are resolvable above noise level (0.1–0.4%) for the actual benchmark logic.

One exception: `java.lang.Class.getEnumConstants` appears consistently at 0.2–0.4% across all `directRandomType` runs, confirming the array-copy allocation overhead is not fully elided.

## Cross-Strategy Ratios

Ratios computed as median/median from the table above.

| typeCount | cached / sharedSecrets | direct / cached | direct / sharedSecrets |
|---|---|---|---|
| 10 | 1.40x | 1.63x | 2.29x |
| 100 | 1.53x | 1.52x | 2.32x |
| 500 | 1.41x | 1.31x | 1.84x |
| 1000 | 1.46x | 1.20x | 1.76x |

The `sharedSecrets` advantage over `cached` is roughly stable (~1.4–1.5x). The `direct` overhead over `cached` shrinks as typeCount grows because `direct` scales more gently in absolute terms.

## Scalability Degradation

Absolute and relative cost increase from typeCount=10 to typeCount=1000:

| Strategy | Cost at 10 (ns/op) | Cost at 1000 (ns/op) | Absolute increase | Relative increase |
|---|---|---|---|---|
| sharedSecretsRandomType | 2.976 | 4.713 | +1.737 ns | +58% |
| cachedLookupRandomType | 4.177 | 6.884 | +2.707 ns | +65% |
| directRandomType | 6.807 | 8.275 | +1.468 ns | +22% |

`direct` degrades least in relative terms because its baseline cost is dominated by the fixed `Class.getEnumConstants()` clone, which is type-count-independent. `sharedSecrets` and `cached` degrade more because their costs are more sensitive to working set size.

## Degradation Threshold

The step-up is concentrated between typeCount=100 and typeCount=500 for all three strategies:

| Strategy | 10→100 (ns) | 100→500 (ns) | 500→1000 (ns) |
|---|---|---|---|
| sharedSecretsRandomType | +0.064 | +1.353 | +0.320 |
| cachedLookupRandomType | +0.463 | +1.543 | +0.701 |
| directRandomType | +0.253 | +1.039 | +0.176 |

The inflection point is between 100 and 500 registered types for all strategies. Costs are nearly flat from 10 to 100, then jump sharply, and stabilize again from 500 to 1000. This pattern is consistent with a CPU data cache working set crossing a threshold around typeCount~100–500 (with enumSize=16, 500 types × 16 constants × ~8 bytes/ref ≈ 64 KB, near typical L1/L2 boundaries). The degradation is not linear with typeCount, ruling out O(n) traversal as the cause; it is more consistent with cache line pressure or ClassValue hash table behavior under increased working set.
