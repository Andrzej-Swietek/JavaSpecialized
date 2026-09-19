package dev.specialize.processor.model;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record PrimitiveTarget(TypeTag tag, String key, String boxed, String suffix) implements TargetType {
    private static final Map<String, PrimitiveTarget> BY_NAME = Map.of(
            "int", new PrimitiveTarget(TypeTag.INT, "int", "java.lang.Integer", "Int"),
            "long", new PrimitiveTarget(TypeTag.LONG, "long", "java.lang.Long", "Long"),
            "double", new PrimitiveTarget(TypeTag.DOUBLE, "double", "java.lang.Double", "Double"),
            "boolean", new PrimitiveTarget(TypeTag.BOOLEAN, "boolean", "java.lang.Boolean", "Boolean"),
            "byte", new PrimitiveTarget(TypeTag.BYTE, "byte", "java.lang.Byte", "Byte"),
            "short", new PrimitiveTarget(TypeTag.SHORT, "short", "java.lang.Short", "Short"),
            "char", new PrimitiveTarget(TypeTag.CHAR, "char", "java.lang.Character", "Char"),
            "float", new PrimitiveTarget(TypeTag.FLOAT, "float", "java.lang.Float", "Float"));

    public static List<PrimitiveTarget> all() {
        return BY_NAME.values().stream().sorted(Comparator.comparing(PrimitiveTarget::key)).toList();
    }

    public static PrimitiveTarget of(String name) {
        return Optional.ofNullable(BY_NAME.get(name)).orElseThrow(() -> new IllegalArgumentException("not a primitive: " + name));
    }

    /** Empty for {@code void}. */
    public static Optional<PrimitiveTarget> of(TypeTag tag) {
        return BY_NAME.values().stream().filter(p -> p.tag == tag).findFirst();
    }

    public static Optional<PrimitiveTarget> ofBox(String qualifiedBoxName) {
        return BY_NAME.values().stream().filter(p -> p.boxed.equals(qualifiedBoxName)).findFirst();
    }

    public boolean isFloating() {
        return tag == TypeTag.DOUBLE || tag == TypeTag.FLOAT;
    }

    /** The {@code Int} in {@code IntPredicate}; only {@code int}, {@code long}, {@code double} and {@code boolean} have such interfaces. */
    public Optional<String> jdkFunctionalPrefix() {
        return switch (tag) {
            case INT, LONG, DOUBLE, BOOLEAN -> Optional.of(suffix);
            default -> Optional.empty();
        };
    }
}
