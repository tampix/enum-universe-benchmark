package bench;

import org.objectweb.asm.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * Generates a minimal enum class with the given number of constants.
 *
 * <p>Produces valid bytecode equivalent to:
 * <pre>{@code
 * public enum GeneratedEnumN {
 *     V0, V1, ..., Vk;
 * }
 * }</pre>
 */
final class EnumClassGenerator {

    /**
     * @param className     fully qualified internal name (e.g. "bench/GeneratedEnum42")
     * @param constantCount number of enum constants to generate
     * @return raw class bytes
     */
    static byte[] generate(String className, int constantCount) {
        var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        var descriptor = "L" + className + ";";
        var signature = "Ljava/lang/Enum<" + descriptor + ">;";

        cw.visit(V17, ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_ENUM,
                className, signature, "java/lang/Enum", null);

        // Enum constant fields
        for (int i = 0; i < constantCount; i++) {
            cw.visitField(
                    ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_ENUM,
                    "V" + i, descriptor, null, null);
        }

        // $VALUES field
        var arrayDescriptor = "[" + descriptor;
        cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
                "$VALUES", arrayDescriptor, null, null);

        // Static initializer — instantiate each constant + populate $VALUES
        var clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();

        for (int i = 0; i < constantCount; i++) {
            clinit.visitTypeInsn(NEW, className);
            clinit.visitInsn(DUP);
            clinit.visitLdcInsn("V" + i);
            pushInt(clinit, i);
            clinit.visitMethodInsn(INVOKESPECIAL, className,
                    "<init>", "(Ljava/lang/String;I)V", false);
            clinit.visitFieldInsn(PUTSTATIC, className, "V" + i, descriptor);
        }

        // $VALUES = new E[] { V0, V1, ... }
        pushInt(clinit, constantCount);
        clinit.visitTypeInsn(ANEWARRAY, className);
        for (int i = 0; i < constantCount; i++) {
            clinit.visitInsn(DUP);
            pushInt(clinit, i);
            clinit.visitFieldInsn(GETSTATIC, className, "V" + i, descriptor);
            clinit.visitInsn(AASTORE);
        }
        clinit.visitFieldInsn(PUTSTATIC, className, "$VALUES", arrayDescriptor);

        clinit.visitInsn(RETURN);
        clinit.visitMaxs(-1, -1); // COMPUTE_FRAMES handles this
        clinit.visitEnd();

        // Constructor — just delegates to Enum.<init>
        var init = cw.visitMethod(ACC_PRIVATE, "<init>",
                "(Ljava/lang/String;I)V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitVarInsn(ALOAD, 1);
        init.visitVarInsn(ILOAD, 2);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Enum",
                "<init>", "(Ljava/lang/String;I)V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(-1, -1);
        init.visitEnd();

        // values() — required by enum contract
        var values = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "values", "()" + arrayDescriptor, null, null);
        values.visitCode();
        values.visitFieldInsn(GETSTATIC, className, "$VALUES", arrayDescriptor);
        values.visitMethodInsn(INVOKEVIRTUAL, arrayDescriptor,
                "clone", "()Ljava/lang/Object;", false);
        values.visitTypeInsn(CHECKCAST, arrayDescriptor);
        values.visitInsn(ARETURN);
        values.visitMaxs(-1, -1);
        values.visitEnd();

        // valueOf(String)
        var valueOf = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
                "valueOf", "(Ljava/lang/String;)" + descriptor, null, null);
        valueOf.visitCode();
        valueOf.visitLdcInsn(Type.getObjectType(className));
        valueOf.visitVarInsn(ALOAD, 0);
        valueOf.visitMethodInsn(INVOKESTATIC, "java/lang/Enum",
                "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        valueOf.visitTypeInsn(CHECKCAST, className);
        valueOf.visitInsn(ARETURN);
        valueOf.visitMaxs(-1, -1);
        valueOf.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void pushInt(MethodVisitor mv, int value) {
        if (value <= 5)           mv.visitInsn(ICONST_0 + value);
        else if (value <= 127)    mv.visitIntInsn(BIPUSH, value);
        else if (value <= 32767)  mv.visitIntInsn(SIPUSH, value);
        else                      mv.visitLdcInsn(value);
    }
}
