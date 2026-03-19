package bench;

final class EnumClassLoader extends ClassLoader {

    EnumClassLoader(ClassLoader parent) {
        super(parent);
    }

    @SuppressWarnings("unchecked")
    <E extends Enum<E>> Class<E> defineEnum(String name, int constantCount) {
        var internalName = name.replace('.', '/');
        var bytes = EnumClassGenerator.generate(internalName, constantCount);
        return (Class<E>) defineClass(name, bytes, 0, bytes.length);
    }
}
