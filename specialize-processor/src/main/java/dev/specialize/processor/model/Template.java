package dev.specialize.processor.model;

import dev.specialize.BoxedArguments;
import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree.JCExpression;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.lang.model.element.TypeElement;

/** A class annotated with {@code @Specialize}. */
public sealed interface Template permits SourceTemplate, BinaryTemplate {

    /** One type parameter of a template; a specialized one is substituted. */
    record TypeParameter(String name, boolean specialized, List<TargetType> types) {
        public TypeParameter {
            types = List.copyOf(types);
        }

        public static TypeParameter generic(String name) {
            return new TypeParameter(name, false, List.of());
        }

        public static TypeParameter specialized(String name, List<TargetType> types) {
            return new TypeParameter(name, true, types);
        }
    }

    String qualified();

    String namePattern();

    /** All type parameters in declaration order. */
    List<TypeParameter> parameters();

    /** The combinations to generate; a {@link SourceTemplate} with {@code autoscan} grows this list. */
    List<TargetTuple> tuples();

    boolean autoscan();

    BoxedArguments boxedArguments();

    /** Empty for a synthetic template that stands for a {@code @Specialize} method's owner. */
    Optional<TypeElement> element();

    default String simple() {
        return TreeUtil.simpleName(qualified());
    }

    default String pkg() {
        return TreeUtil.packageOf(qualified());
    }

    default List<TypeParameter> specialized() {
        return parameters().stream().filter(TypeParameter::specialized).toList();
    }

    /** Type parameters the generated classes keep, e.g. {@code V} of {@code Map2Int<V>}. */
    default List<String> genericParameterNames() {
        return parameters().stream().filter(p -> !p.specialized()).map(TypeParameter::name).toList();
    }

    default String conventionalName(TargetTuple tuple) {
        String simpleName = NamePattern.expand(namePattern(), simple(), tuple.suffix());
        return pkg().isEmpty() ? simpleName : pkg() + "." + simpleName;
    }

    /**
     * Whether a type argument written as {@code arg} selects the specialization for {@code target}. Through a
     * {@code standsFor} alias ({@code List<int>}) only the primitive spelling counts, whatever the policy.
     */
    default boolean selects(TargetType target, JCExpression arg, boolean viaAlias) {
        if (viaAlias) {
            return TreeUtil.isMarkedPrimitive(arg);
        }
        return !(target instanceof PrimitiveTarget) || TreeUtil.isMarkedPrimitive(arg) || boxedArguments() == BoxedArguments.SPECIALIZE;
    }

    /** The tuple a usage {@code Map2<Integer, String>} asks for: every specialized position must name a selectable target. */
    default Optional<TargetTuple> tupleFor(List<JCExpression> typeArguments, NameResolver resolver, boolean viaAlias) {
        if (typeArguments.size() != parameters().size()) {
            return Optional.empty();
        }
        List<Optional<TargetType>> targets = IntStream.range(0, typeArguments.size())
                .filter(i -> parameters().get(i).specialized())
                .mapToObj(i -> TargetType.classify(resolver, typeArguments.get(i)).filter(t -> selects(t, typeArguments.get(i), viaAlias)))
                .toList();
        return targets.stream().allMatch(Optional::isPresent)
                ? Optional.of(new TargetTuple(targets.stream().map(Optional::orElseThrow).toList()))
                : Optional.empty();
    }

    /** The type arguments a generated class still takes: those at the generic positions. */
    default <T> List<T> genericArguments(List<T> typeArguments) {
        return IntStream.range(0, typeArguments.size()).filter(i -> !parameters().get(i).specialized()).mapToObj(typeArguments::get).toList();
    }
}
