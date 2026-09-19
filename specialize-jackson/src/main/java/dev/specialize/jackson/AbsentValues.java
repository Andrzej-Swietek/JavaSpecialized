package dev.specialize.jackson;

import dev.specialize.Absent;
import dev.specialize.Specialize;
import dev.specialize.Specialized;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** The {@code @Absent} factory of a template or specialization, as a supplier of its value. */
final class AbsentValues {
    private AbsentValues() {
    }

    /** @throws IllegalStateException when the class declares more than one such factory */
    static Optional<Supplier<Object>> of(Class<?> type) {
        if (!type.isAnnotationPresent(Specialized.class) && !type.isAnnotationPresent(Specialize.class)) {
            return Optional.empty();
        }
        List<Method> factories = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Absent.class) && Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0)
                .sorted(Comparator.comparing(Method::getName))
                .toList();
        if (factories.size() > 1) {
            throw new IllegalStateException(type.getName() + " declares " + factories.size() + " no-argument @Absent factories ("
                    + factories.stream().map(Method::getName).collect(Collectors.joining(", ")) + "); keep one");
        }
        return factories.stream().findFirst().map(AbsentValues::supplier);
    }

    private static Supplier<Object> supplier(Method factory) {
        factory.setAccessible(true);
        return () -> {
            try {
                return factory.invoke(null);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("@Absent factory " + factory + " failed", e);
            }
        };
    }
}
