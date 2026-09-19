package dev.specialize.processor.specialize;

import dev.specialize.processor.model.PrimitiveTarget;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** {@code java.util.function} interfaces over {@code T} and their primitive counterparts. */
public final class FunctionalInterfaces {
    private static final String PACKAGE = "java.util.function.";
    private static final String FUNCTION_NAME = "Function";

    public enum Kind {
        FUNCTION("%sFunction", "apply", false), TO_FUNCTION("To%sFunction", "apply", true), PREDICATE("%sPredicate", "test", false),
        CONSUMER("%sConsumer", "accept", false), SUPPLIER("%sSupplier", "get", true), UNARY("%sUnaryOperator", "apply", true),
        BINARY("%sBinaryOperator", "apply", true);

        private final String namePattern;
        private final String method;
        private final boolean renames;

        Kind(String namePattern, String method, boolean renames) {
            this.namePattern = namePattern;
            this.method = method;
            this.renames = renames;
        }

        String primitiveName(String prefix) {
            return PACKAGE + namePattern.formatted(prefix);
        }
    }

    private static final Map<String, Kind> SINGLE_ARGUMENT = Map.of("Predicate", Kind.PREDICATE, "Consumer", Kind.CONSUMER,
            "Supplier", Kind.SUPPLIER, "UnaryOperator", Kind.UNARY, "BinaryOperator", Kind.BINARY);

    /** {@code Supplier<K>} with {@code K → int}: the {@code IntSupplier} shape and the primitive it is for. */
    public record Mapping(Kind kind, PrimitiveTarget target) {
        /** {@code get} → {@code getAsInt} where the primitive interface changed the method name. */
        public Optional<Name> renamed(Name called, Names names) {
            return kind.renames && called.contentEquals(kind.method)
                    ? Optional.of(names.fromString(kind.method + "As" + target.suffix()))
                    : Optional.empty();
        }
    }

    private FunctionalInterfaces() {
    }

    /**
     * The mapping for a type such as {@code Function<T, R>}; {@code targetOf} tells which primitive a type argument
     * stands for. Empty when the JDK has no such interface ({@code short}, {@code Predicate<Boolean>}, or
     * {@code Function<K, V>} over two different primitives).
     */
    public static Optional<Mapping> kindOf(JCTree type, Function<JCTree, Optional<PrimitiveTarget>> targetOf) {
        return TreeUtil.asTypeApply(type).flatMap(ta -> mappingOf(ta, targetOf))
                .filter(mapping -> mapping.target().jdkFunctionalPrefix().isPresent())
                .filter(mapping -> mapping.target().tag() != TypeTag.BOOLEAN || mapping.kind() == Kind.SUPPLIER);
    }

    private static Optional<Mapping> mappingOf(JCTypeApply ta, Function<JCTree, Optional<PrimitiveTarget>> targetOf) {
        return TreeUtil.flatten(ta.clazz)
                .map(n -> n.startsWith(PACKAGE) ? n.substring(PACKAGE.length()) : n)
                .filter(name -> name.equals(FUNCTION_NAME) || SINGLE_ARGUMENT.containsKey(name))
                .flatMap(name -> {
                    Optional<PrimitiveTarget> first = targetOf.apply(ta.arguments.head);
                    Optional<PrimitiveTarget> second = ta.arguments.size() > 1 ? targetOf.apply(ta.arguments.get(1)) : Optional.empty();
                    if (first.isPresent() && second.isPresent() && !first.equals(second)) {
                        return Optional.empty();
                    }
                    Optional<Kind> kind = name.equals(FUNCTION_NAME)
                            ? functionKind(first.isPresent(), second.isPresent())
                            : Optional.of(SINGLE_ARGUMENT.get(name)).filter(_ -> first.isPresent());
                    return kind.map(k -> new Mapping(k, first.or(() -> second).orElseThrow()));
                });
    }

    private static Optional<Kind> functionKind(boolean argumentIsT, boolean resultIsT) {
        if (argumentIsT) {
            return Optional.of(resultIsT ? Kind.UNARY : Kind.FUNCTION);
        }
        return resultIsT ? Optional.of(Kind.TO_FUNCTION) : Optional.empty();
    }

    /** The primitive interface; {@code translateBoxed} converts the remaining type argument ({@code R} of {@code IntFunction<R>}). */
    public static JCExpression mapped(Mapping mapping, JCTypeApply original, TreeMaker make, Names names,
                               UnaryOperator<JCExpression> translateBoxed) {
        JCExpression raw = TreeUtil.qualIdent(make, names, mapping.kind().primitiveName(mapping.target().jdkFunctionalPrefix().orElseThrow()));
        return switch (mapping.kind()) {
            case FUNCTION -> make.TypeApply(raw, List.of(translateBoxed.apply(original.arguments.get(1))));
            case TO_FUNCTION -> make.TypeApply(raw, List.of(translateBoxed.apply(original.arguments.get(0))));
            default -> raw;
        };
    }
}
