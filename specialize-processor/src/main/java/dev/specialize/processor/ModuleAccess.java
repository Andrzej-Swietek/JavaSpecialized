package dev.specialize.processor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Makes the internal javac packages accessible to this (unnamed) module. When javac was started with
 * {@code --add-exports} (or {@code .mvn/jvm.config} / Gradle fork args) nothing happens.
 */
public final class ModuleAccess {
    public static final List<String> PACKAGES = List.of(
            "com.sun.tools.javac.tree",
            "com.sun.tools.javac.util",
            "com.sun.tools.javac.code",
            "com.sun.tools.javac.processing",
            "com.sun.tools.javac.parser",
            "com.sun.tools.javac.api",
            "com.sun.tools.javac.comp");

    /** Strategy that exports {@code pkg} of {@code from} to {@code to} by whatever means. */
    public interface Exporter {
        void export(Module from, String pkg, Module to) throws Throwable;
    }

    private ModuleAccess() {
    }

    public static void ensureOpen() {
        ensureOpen(ModuleLayer.boot(), ModuleAccess.class.getModule(), PACKAGES, ModuleAccess::exportWithUnsafe);
    }

    public static void ensureOpen(ModuleLayer layer, Module self, List<String> packages, Exporter exporter) {
        Module jdkCompiler = layer.findModule("jdk.compiler").orElseThrow(() -> new IllegalStateException(
                "jdk.compiler module not found; the specialize processor needs a JDK javac"));
        List<String> closed = packages.stream().filter(pkg -> !jdkCompiler.isExported(pkg, self)).toList();
        try {
            for (String pkg : closed) { // Exporter.export throws Throwable (MethodHandle.invoke)
                exporter.export(jdkCompiler, pkg, self);
            }
        } catch (Throwable t) {
            throw new IllegalStateException(failureMessage(t, packages), t);
        }
    }

    private static String failureMessage(Throwable cause, List<String> packages) {
        return "specialize: cannot access javac internals (" + cause + "). Start javac with:\n"
                + packages.stream().map(pkg -> "  -J--add-exports=jdk.compiler/" + pkg + "=ALL-UNNAMED").collect(Collectors.joining("\n", "", "\n"))
                + "(Maven: put the --add-exports lines in .mvn/jvm.config; Gradle: options.fork = true and forkOptions.jvmArgs)";
    }

    /** {@code Module.implAddExports} reached through {@code MethodHandles.Lookup.IMPL_LOOKUP}, read with {@code sun.misc.Unsafe}. */
    public static void exportWithUnsafe(Module from, String pkg, Module to) throws Throwable {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        Object base = unsafeClass.getMethod("staticFieldBase", Field.class).invoke(unsafe, implLookup);
        long offset = (Long) unsafeClass.getMethod("staticFieldOffset", Field.class).invoke(unsafe, implLookup);
        MethodHandles.Lookup lookup = (MethodHandles.Lookup) unsafeClass
                .getMethod("getObject", Object.class, long.class).invoke(unsafe, base, offset);
        MethodHandle addExports = lookup.findVirtual(Module.class, "implAddExports",
                MethodType.methodType(void.class, String.class, Module.class));
        addExports.invoke(from, pkg, to);
    }
}
