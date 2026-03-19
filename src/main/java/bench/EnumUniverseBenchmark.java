package bench;

import jdk.internal.access.SharedSecrets;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsPrepend = "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
@State(Scope.Benchmark)
public class EnumUniverseBenchmark {

    // -------------------------------------------------------------------------
    // Enum size selector
    // -------------------------------------------------------------------------

    @Param({"4", "16", "64", "256"})
    int enumSize;

    @Param({"100", "10000"})
    int iterations;

    private Class<? extends Enum<?>> enumClass;

    @Setup(Level.Trial)
    public void setup() {
        enumClass = switch (enumSize) {
            case 4   -> Enum4.class;
            case 16  -> Enum16.class;
            case 64  -> Enum64.class;
            case 256 -> Enum256.class;
            default  -> throw new IllegalArgumentException("Unsupported enumSize: " + enumSize);
        };
    }

    // -------------------------------------------------------------------------
    // Baseline A: SharedSecrets — the zero-copy internal JDK path.
    // This is what java.util.EnumSet uses internally. Inaccessible from library
    // code without --add-exports (not acceptable for a public library).
    // Represents the theoretical minimum cost for the universe lookup.
    // -------------------------------------------------------------------------

    @Benchmark
    @SuppressWarnings("unchecked")
    public Enum<?>[] sharedSecrets() {
        return getUniverse((Class<? extends Enum>) enumClass);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E[] getUniverse(Class<E> elementType) {
        return SharedSecrets.getJavaLangAccess().getEnumConstantsShared(elementType);
    }

    // -------------------------------------------------------------------------
    // Strategy A: direct — Class.getEnumConstants() every time
    // -------------------------------------------------------------------------

    @Benchmark
    public Enum<?>[] direct() {
        return enumClass.getEnumConstants();
    }

    // -------------------------------------------------------------------------
    // Strategy B: cached — ClassValue lookup
    // -------------------------------------------------------------------------

    private static final ClassValue<Enum<?>[]> CACHE = new ClassValue<>() {
        @Override
        protected Enum<?>[] computeValue(Class<?> type) {
            return (Enum<?>[]) type.getEnumConstants();
        }
    };

    @Benchmark
    public Enum<?>[] cached() {
        return CACHE.get(enumClass);
    }

    // -------------------------------------------------------------------------
    // Hot loop variant — simulates repeated construction (e.g. groupBy path)
    // -------------------------------------------------------------------------

    @Benchmark
    public int directHotLoop() {
        int sum = 0;
        for (int i = 0; i < iterations; i++) {
            sum += enumClass.getEnumConstants().length;
        }
        return sum;
    }

    @Benchmark
    public int cachedHotLoop() {
        int sum = 0;
        for (int i = 0; i < iterations; i++) {
            sum += CACHE.get(enumClass).length;
        }
        return sum;
    }

    // -------------------------------------------------------------------------
    // Enum definitions
    // -------------------------------------------------------------------------

    enum Enum4 { A, B, C, D }

    enum Enum16 { A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P }

    enum Enum64 {
        V0,  V1,  V2,  V3,  V4,  V5,  V6,  V7,  V8,  V9,
        V10, V11, V12, V13, V14, V15, V16, V17, V18, V19,
        V20, V21, V22, V23, V24, V25, V26, V27, V28, V29,
        V30, V31, V32, V33, V34, V35, V36, V37, V38, V39,
        V40, V41, V42, V43, V44, V45, V46, V47, V48, V49,
        V50, V51, V52, V53, V54, V55, V56, V57, V58, V59,
        V60, V61, V62, V63
    }

    enum Enum256 {
        V0,   V1,   V2,   V3,   V4,   V5,   V6,   V7,   V8,   V9,
        V10,  V11,  V12,  V13,  V14,  V15,  V16,  V17,  V18,  V19,
        V20,  V21,  V22,  V23,  V24,  V25,  V26,  V27,  V28,  V29,
        V30,  V31,  V32,  V33,  V34,  V35,  V36,  V37,  V38,  V39,
        V40,  V41,  V42,  V43,  V44,  V45,  V46,  V47,  V48,  V49,
        V50,  V51,  V52,  V53,  V54,  V55,  V56,  V57,  V58,  V59,
        V60,  V61,  V62,  V63,  V64,  V65,  V66,  V67,  V68,  V69,
        V70,  V71,  V72,  V73,  V74,  V75,  V76,  V77,  V78,  V79,
        V80,  V81,  V82,  V83,  V84,  V85,  V86,  V87,  V88,  V89,
        V90,  V91,  V92,  V93,  V94,  V95,  V96,  V97,  V98,  V99,
        V100, V101, V102, V103, V104, V105, V106, V107, V108, V109,
        V110, V111, V112, V113, V114, V115, V116, V117, V118, V119,
        V120, V121, V122, V123, V124, V125, V126, V127, V128, V129,
        V130, V131, V132, V133, V134, V135, V136, V137, V138, V139,
        V140, V141, V142, V143, V144, V145, V146, V147, V148, V149,
        V150, V151, V152, V153, V154, V155, V156, V157, V158, V159,
        V160, V161, V162, V163, V164, V165, V166, V167, V168, V169,
        V170, V171, V172, V173, V174, V175, V176, V177, V178, V179,
        V180, V181, V182, V183, V184, V185, V186, V187, V188, V189,
        V190, V191, V192, V193, V194, V195, V196, V197, V198, V199,
        V200, V201, V202, V203, V204, V205, V206, V207, V208, V209,
        V210, V211, V212, V213, V214, V215, V216, V217, V218, V219,
        V220, V221, V222, V223, V224, V225, V226, V227, V228, V229,
        V230, V231, V232, V233, V234, V235, V236, V237, V238, V239,
        V240, V241, V242, V243, V244, V245, V246, V247, V248, V249,
        V250, V251, V252, V253, V254, V255
    }
}
