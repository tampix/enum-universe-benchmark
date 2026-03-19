package bench;

import jdk.internal.access.SharedSecrets;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsPrepend = "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
@State(Scope.Benchmark)
public class EnumUniverseCacheScalabilityBenchmark {

    // Number of distinct enum types in the cache
    @Param({"10", "100", "500", "1000"})
    int typeCount;

    // Constants per enum (fixed — isolate the type count variable)
    @Param({"16"})
    int enumSize;

    private Class<?>[] enumClasses;

    // The cache under test
    private static final ClassValue<Enum<?>[]> CACHE = new ClassValue<>() {
        @Override
        protected Enum<?>[] computeValue(Class<?> type) {
            return (Enum<?>[]) type.getEnumConstants();
        }
    };

    @Setup(Level.Trial)
    public void setup() {
        var loader = new EnumClassLoader(getClass().getClassLoader());
        enumClasses = new Class<?>[typeCount];
        for (int i = 0; i < typeCount; i++) {
            enumClasses[i] = loader.defineEnum("bench.GenEnum" + i, enumSize);
            // Pre-warm the cache so we measure steady-state lookup, not population
            CACHE.get(enumClasses[i]);
        }
    }

    /**
     * Lookup a random cached type — measures ClassValue.get() latency
     * as the internal table grows.
     */
    @Benchmark
    public Enum<?>[] cachedLookupRandomType(Blackhole bh) {
        var cls = enumClasses[ThreadLocalRandom.current().nextInt(typeCount)];
        return CACHE.get(cls);
    }

    /**
     * Baseline — getEnumConstants() on a random type (no cache).
     */
    @Benchmark
    @SuppressWarnings("unchecked")
    public Enum<?>[] directRandomType(Blackhole bh) {
        var cls = enumClasses[ThreadLocalRandom.current().nextInt(typeCount)];
        return (Enum<?>[]) cls.getEnumConstants();
    }

    /**
     * JVM baseline — direct SharedSecrets path (zero-copy, internal JDK API).
     * Shows the floor that library code cannot reach without --add-exports.
     */
    @Benchmark
    @SuppressWarnings("unchecked")
    public Enum<?>[] sharedSecretsRandomType(Blackhole bh) {
        var cls = enumClasses[ThreadLocalRandom.current().nextInt(typeCount)];
        return getUniverse((Class<? extends Enum>) cls);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E[] getUniverse(Class<E> elementType) {
        return SharedSecrets.getJavaLangAccess().getEnumConstantsShared(elementType);
    }
}
