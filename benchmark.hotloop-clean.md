# Hot Loop Benchmark Synthesis

## Environment

| Parameter | Value |
|---|---|
| JMH version | 1.37 |
| JVM | OpenJDK 64-Bit Server VM, JDK 17.0.18+8 |
| JVM options | `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED -Xms256m -Xmx256m` |
| Blackhole mode | Compiler (auto-detected) |
| Benchmark mode | Average time (ns/op) |
| Warmup | 5 iterations × 1 s |
| Measurement | 5 iterations × 1 s |
| Forks | 2 (10 samples total per result) |
| GC profiler | `gc.alloc.rate`, `gc.alloc.rate.norm`, `gc.count`, `gc.time` |

## Strategies

- **cachedHotLoop** — resolves the enum array via `ClassValue<Enum<?>[]>` lookup per benchmark call; zero heap allocation.
- **directHotLoop** — calls `Class.getEnumConstants()` per loop iteration; allocates and copies a fresh reference array on every call.

---

## Raw results

### cachedHotLoop

| enumSize | iterations | ns/op | ± error | ns/iter | alloc/op (B) | alloc rate (MB/s) | GC count | GC time |
|---|---|---:|---:|---:|---:|---:|---:|---|
| 4   | 100   | 111.232 | 0.475 | 1.112 | 0.001 | 0.006 | ≈ 0 | — |
| 4   | 10,000 | 11,404.190 | 120.905 | 1.140 | 0.067 | 0.006 | ≈ 0 | — |
| 16  | 100   | 111.313 | 1.591 | 1.113 | 0.001 | 0.006 | ≈ 0 | — |
| 16  | 10,000 | 11,373.026 | 181.314 | 1.137 | 0.067 | 0.006 | ≈ 0 | — |
| 64  | 100   | 111.026 | 0.627 | 1.110 | 0.001 | 0.006 | ≈ 0 | — |
| 64  | 10,000 | 11,303.622 | 71.062 | 1.130 | 0.067 | 0.006 | ≈ 0 | — |
| 256 | 100   | 111.059 | 0.460 | 1.111 | 0.001 | 0.006 | ≈ 0 | — |
| 256 | 10,000 | 11,312.802 | 69.206 | 1.131 | 0.067 | 0.006 | ≈ 0 | — |

### directHotLoop

| enumSize | iterations | ns/op | ± error | ns/iter | alloc/op (B) | alloc rate (MB/s) | GC count (sum/10) | GC time (sum/10) |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 4   | 100   | 382.886 | 36.586 | 3.829 | 3,200.002 | 7,994.871 | 520 | 143 ms |
| 4   | 10,000 | 35,887.341 | 1,712.975 | 3.589 | 320,000.214 | 8,507.936 | 552 | 149 ms |
| 16  | 100   | 469.726 | 14.157 | 4.697 | 8,000.003 | 16,241.810 | 1,060 | 213 ms |
| 16  | 10,000 | 46,114.258 | 951.913 | 4.611 | 800,000.275 | 16,541.397 | 1,080 | 244 ms |
| 64  | 100   | 1,541.939 | 10.525 | 15.419 | 27,200.009 | 16,817.175 | 1,097 | 255 ms |
| 64  | 10,000 | 153,398.302 | 1,310.686 | 15.340 | 2,720,000.912 | 16,904.614 | 1,104 | 262 ms |
| 256 | 100   | 5,823.631 | 24.302 | 58.236 | 104,000.035 | 17,024.173 | 1,111 | 272 ms |
| 256 | 10,000 | 578,943.846 | 2,809.840 | 57.894 | 10,400,003.441 | 17,125.679 | 1,119 | 268 ms |

---

## Speed ratio (direct / cached)

| enumSize | iterations | cached ns/op | direct ns/op | ratio |
|---|---|---:|---:|---:|
| 4   | 100   | 111.232 | 382.886 | **3.44×** |
| 4   | 10,000 | 11,404.190 | 35,887.341 | **3.15×** |
| 16  | 100   | 111.313 | 469.726 | **4.22×** |
| 16  | 10,000 | 11,373.026 | 46,114.258 | **4.05×** |
| 64  | 100   | 111.026 | 1,541.939 | **13.88×** |
| 64  | 10,000 | 11,303.622 | 153,398.302 | **13.57×** |
| 256 | 100   | 111.059 | 5,823.631 | **52.44×** |
| 256 | 10,000 | 11,312.802 | 578,943.846 | **51.18×** |

---

## Scaling behaviour

**cachedHotLoop is O(1) in enumSize.** Across all four enum sizes (4 to 256, a 64× range), total op time is flat at ~111 ns for 100 iterations and ~11,300–11,400 ns for 10,000 iterations. Per-iteration cost is stable at ~1.11–1.14 ns regardless of enum size or iteration count. The negligible alloc/op (0.001 B at 100 iters, 0.067 B at 10,000 iters) is JMH/benchmark overhead, not enum-lookup overhead.

**directHotLoop scales linearly with enumSize** in both time and allocation. The per-iteration cost:

| enumSize | ns/iter (100 iters) | ns/iter (10,000 iters) | alloc/iter (B) |
|---|---:|---:|---:|
| 4   | 3.83 | 3.59 | 32 |
| 16  | 4.70 | 4.61 | 80 |
| 64  | 15.42 | 15.34 | 272 |
| 256 | 58.24 | 57.89 | 1,040 |

The alloc/iter values correspond to `enumSize × 8 bytes`, confirming that `getEnumConstants()` allocates a new reference array of that length on every call. At 10,000 iterations and enumSize = 256, a single benchmark op allocates ~9.9 MB.

---

## GC data

`cachedHotLoop` produces no GC activity at any configuration (count ≈ 0, alloc rate 0.006 MB/s noise floor, no gc.time recorded).

`directHotLoop` generates sustained GC pressure:

- The alloc rate saturates at roughly 8,000–17,000 MB/s across all configurations, reflecting the JVM allocating and collecting as fast as execution allows.
- GC fires approximately 52–112 times per 1-second measurement window (520–1,119 events summed over 10 samples).
- GC time sums over the 10-sample run range from 143 ms (enumSize = 4, iters = 100) to 272 ms (enumSize = 256, iters = 100), meaning the process spent a non-trivial fraction of each second pausing for collection.
- GC count and time are roughly stable across enumSize once above 4 constants, because the allocation rate in MB/s also stabilises; the difference between configurations is mainly the granularity (fewer, larger allocations vs. more, smaller ones).

---

## Outliers and variance anomalies

- **directHotLoop, enumSize = 4, iters = 100**: highest relative error at ±36.6 ns on 382.9 ns (±9.6%). Fork 1 measurements clustered around 381–425 ns; fork 2 around 356–370 ns — a ~10% inter-fork gap, likely from GC pause timing differences amplified by the small absolute op time. No other configuration shows inter-fork divergence at this scale.

- **directHotLoop, enumSize = 4, iters = 10,000**: warmup shows bimodality (iterations 1–3 at ~26–27 µs, then iterations 4–5 jump to ~35–37 µs) in fork 1, with fork 2 warmup already starting higher. This suggests a late JIT recompilation or GC mode transition during warmup. Fork 2 measurement iterations are ~6–7% slower than fork 1, contributing to ±1,712 ns error (±4.8%).

- **cachedHotLoop, enumSize = 16, iters = 100**: one measurement point at 114.175 ns stands out against a cluster of 110–112 ns values in fork 2. The aggregate error of ±1.591 ns (±1.4%) is the largest among cached configurations but is negligible in absolute terms.

- All remaining configurations have errors below 2% (cached) or 2.1% (direct at larger enumSize), with very tight stdev.

---

## Summary

`cachedHotLoop` (ClassValue-based cache) is allocation-free, GC-free, and enumSize-invariant. At enumSize = 256 it is ~52× faster per operation than `directHotLoop`. The advantage is purely structural: one cache lookup returns a stable reference whereas `getEnumConstants()` always allocates and copies a full array. For workloads that iterate over enum constants in tight loops — groupBy, collect pipelines, enum-keyed dispatch — the ClassValue cache eliminates both the allocation cost and the GC stalls that compound it.
