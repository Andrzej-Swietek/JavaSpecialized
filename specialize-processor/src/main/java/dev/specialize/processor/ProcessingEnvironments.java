package dev.specialize.processor;

import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;

/** Finds the real javac environment behind the wrappers Gradle (delegating class) and IntelliJ (proxy) install. */
public final class ProcessingEnvironments {
    private static final int MAX_DEPTH = 4;

    private ProcessingEnvironments() {
    }

    public static JavacProcessingEnvironment unwrap(ProcessingEnvironment env) {
        return unwrap(env, 0);
    }

    private static JavacProcessingEnvironment unwrap(ProcessingEnvironment env, int depth) {
        if (env instanceof JavacProcessingEnvironment javac) {
            return javac;
        }
        if (depth > MAX_DEPTH) {
            throw unsupported(env, "the wrappers nest too deeply");
        }
        Object holder = Proxy.isProxyClass(env.getClass()) ? Proxy.getInvocationHandler(env) : env;
        return fieldOfType(holder, ProcessingEnvironment.class)
                .filter(inner -> inner != env)
                .map(inner -> unwrap((ProcessingEnvironment) inner, depth + 1))
                .orElseThrow(() -> unsupported(env, "not a javac environment and no delegate field found"));
    }

    private static IllegalStateException unsupported(ProcessingEnvironment env, String detail) {
        return new IllegalStateException("specialize: unsupported ProcessingEnvironment " + env.getClass().getName() + " (" + detail
                + "); the processor only runs under javac or a wrapper that keeps the javac environment in a field. Nothing will be specialized.");
    }

    /** The first non-null instance field of the given type, skipping fields the module system keeps from us. */
    public static Optional<Object> fieldOfType(Object holder, Class<?> type) {
        return Stream.<Class<?>>iterate(holder.getClass(), c -> c != Object.class, Class::getSuperclass)
                .flatMap(c -> Stream.of(c.getDeclaredFields()))
                .filter(f -> type.isAssignableFrom(f.getType()) && !Modifier.isStatic(f.getModifiers()))
                .map(f -> read(f, holder))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<Object> read(Field field, Object holder) {
        try {
            field.setAccessible(true);
            return Optional.ofNullable(field.get(holder));
        } catch (InaccessibleObjectException | IllegalAccessException notReadable) {
            return Optional.empty();
        }
    }
}
