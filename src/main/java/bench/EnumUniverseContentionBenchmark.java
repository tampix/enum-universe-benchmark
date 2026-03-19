package bench;

import jdk.internal.access.SharedSecrets;
import org.openjdk.jmh.annotations.*;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

@Threads(4)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsPrepend = "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
@State(Scope.Benchmark)
public class EnumUniverseContentionBenchmark {

    @Param({"4", "16", "64", "256"})
    int enumSize;

    private Class<? extends Enum<?>> enumClass;

    @Setup(Level.Trial)
    public void setup() {
        enumClass = switch (enumSize) {
            case 4   -> EnumUniverseBenchmark.Enum4.class;
            case 16  -> EnumUniverseBenchmark.Enum16.class;
            case 64  -> EnumUniverseBenchmark.Enum64.class;
            case 256 -> EnumUniverseBenchmark.Enum256.class;
            default  -> throw new IllegalArgumentException("Unsupported enumSize: " + enumSize);
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E[] getUniverse(Class<E> elementType) {
        return SharedSecrets.getJavaLangAccess().getEnumConstantsShared(elementType);
    }

    private static final ClassValue<Enum<?>[]> CACHE = new ClassValue<>() {
        @Override
        protected Enum<?>[] computeValue(Class<?> type) {
            return (Enum<?>[]) type.getEnumConstants();
        }
    };

    @Benchmark
    @SuppressWarnings("unchecked")
    public Enum<?>[] sharedSecrets() {
        return getUniverse((Class<? extends Enum>) enumClass);
    }

    @Benchmark
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EnumSet<?> baseline() {
        return EnumSet.noneOf((Class) enumClass);
    }

    @Benchmark
    public Enum<?>[] direct() {
        return enumClass.getEnumConstants();
    }

    @Benchmark
    public Enum<?>[] cached() {
        return CACHE.get(enumClass);
    }
}
