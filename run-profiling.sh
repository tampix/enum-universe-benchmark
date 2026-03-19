#!/usr/bin/env bash
# run-profiling.sh — Targeted profiler runs for enum universe strategy analysis
#
# Produces:
#   benchmark.single-call.log     single construction cost: direct vs cached vs sharedSecrets
#   benchmark.hotloop-clean.log   hot-loop numbers (GC-isolated): direct vs cached
#   benchmark.stack.log           stack profiler on hot-loop at Enum64/10000
#   benchmark.perf.log            cache scalability: ClassValue vs direct vs sharedSecrets
#
# Usage:
#   mvn package -q
#   ./run-profiling.sh

set -euo pipefail

JAR="target/benchmarks.jar"

if [[ ! -f "$JAR" ]]; then
  echo "ERROR: $JAR not found. Run: mvn package -q" >&2
  exit 1
fi

step() {
  echo
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  $*"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo
}

# ─── Run 1: Single-call cost ──────────────────────────────────────────────────
# Measures the raw per-call overhead of each strategy for a single universe
# lookup — the cost paid at collection construction time.
# iterations is a class-level @Param; we pin it to its smallest value (100) so
# the unused parameter does not multiply the run matrix.

step "Run 1/4 — single-call cost  →  benchmark.single-call.log"

java -jar "$JAR" \
  "EnumUniverseBenchmark\.(direct|cached|sharedSecrets)$" \
  -p iterations=100 \
  -wi 5 -i 5 -f 2 \
  -prof gc \
  2>&1 | tee benchmark.single-call.log

# ─── Run 2: Hot loops with GC-isolated measurement ────────────────────────────
# The original run contaminated hot-loop measurements with GC pauses caused by
# the massive allocation from getEnumConstants() (up to 10 MB/iter).
# -gc true forces a GC cycle between each measurement iteration.
# Fixed heap (-Xms/-Xmx) makes GC behaviour deterministic across forks.

step "Run 2/4 — hot loops with clean GC  →  benchmark.hotloop-clean.log"

java -jar "$JAR" \
  "EnumUniverseBenchmark\.(directHotLoop|cachedHotLoop)" \
  -wi 5 -i 5 -f 2 \
  -gc true \
  -jvmArgs "-Xms256m -Xmx256m" \
  -prof gc \
  2>&1 | tee benchmark.hotloop-clean.log

# ─── Run 3: Stack profiler — hot-loop variance at Enum64/10000 ────────────────
# cachedHotLoop (enumSize=64, iterations=10000) showed ±52% stddev in the
# original run despite near-zero allocation. Stack sampling will reveal whether
# this is JIT deoptimisation, an unexpected lock, or scheduler noise.

step "Run 3/4 — stack profiler (hot-loop variance)  →  benchmark.stack.log"

java -jar "$JAR" \
  "EnumUniverseBenchmark\.(directHotLoop|cachedHotLoop)" \
  -p enumSize=64 \
  -p iterations=10000 \
  -wi 5 -i 10 -f 1 \
  -prof stack \
  2>&1 | tee benchmark.stack.log

# ─── Run 4: Cache scalability — confirm LLC-miss hypothesis ───────────────────
# ClassValue.get() degrades from 4.4 → 7.0 ns as typeCount grows 10 → 1000.
# -prof perfnorm (hardware counters) will show cache-misses/op.
# Falls back to -prof stack if perf_event_paranoid > 1.

step "Run 4/4 — cache scalability profiling  →  benchmark.perf.log"

PERF_OK=false
# Dry-run to probe perf availability (single iteration, no output)
if java -jar "$JAR" \
      "EnumUniverseCacheScalabilityBenchmark.cachedLookupRandomType" \
      -p typeCount=10 -p enumSize=16 \
      -wi 1 -i 1 -f 1 \
      -prof perfnorm \
      >/dev/null 2>&1; then
  PERF_OK=true
fi

if $PERF_OK; then
  echo "Hardware perf counters available — using -prof perfnorm"
  java -jar "$JAR" \
    "EnumUniverseCacheScalabilityBenchmark" \
    -wi 5 -i 5 -f 2 \
    -prof perfnorm \
    2>&1 | tee benchmark.perf.log
else
  echo "WARNING: perf_event_paranoid too restrictive, falling back to -prof stack"
  echo "(to enable: sudo sysctl kernel.perf_event_paranoid=1)"
  java -jar "$JAR" \
    "EnumUniverseCacheScalabilityBenchmark" \
    -wi 5 -i 5 -f 2 \
    -prof stack \
    2>&1 | tee benchmark.perf.log
fi

echo
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  All runs complete."
echo
echo "  benchmark.single-call.log"
echo "  benchmark.hotloop-clean.log"
echo "  benchmark.stack.log"
echo "  benchmark.perf.log"
echo
echo "  Next step: analyze each log and produce .md files."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
