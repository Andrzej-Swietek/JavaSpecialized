package dev.specialize.processor.specialize;

import dev.specialize.processor.model.TargetTuple;
import dev.specialize.processor.model.TargetType;
import dev.specialize.processor.model.Template;
import dev.specialize.processor.model.Template.TypeParameter;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCWildcard;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The type variables one specialization substitutes, and what references to the template become there.
 * {@code intoItself}: specializing into the template's own class (guard bridges), where self types stay as written.
 */
record Substitution(Template template, Map<Name, TargetType> targets, String specQualified, boolean intoItself) {

    static Substitution of(Template template, TargetTuple tuple, String specQualified, Names names) {
        java.util.List<TypeParameter> specialized = template.specialized();
        Map<Name, TargetType> targets = IntStream.range(0, specialized.size()).boxed()
                .collect(Collectors.toUnmodifiableMap(i -> names.fromString(specialized.get(i).name()), i -> tuple.targets().get(i)));
        return new Substitution(template, targets, specQualified, specQualified.equals(template.qualified()));
    }

    /** The target a type tree stands for: {@code K} or {@code ? extends K}. */
    Optional<TargetType> targetOf(JCTree tree) {
        return switch (tree) {
            case JCWildcard w when w.inner != null -> targetOf(w.inner);
            case JCIdent id -> Optional.ofNullable(targets.get(id.name));
            default -> Optional.empty();
        };
    }

    /** {@code T} or {@code T[]}: a value the {@code Prim} helpers may be applied to, and its target. */
    Optional<TargetType> valueTarget(JCTree type) {
        return targetOf(TreeUtil.elementType(type));
    }

    boolean substitutes(Name variable) {
        return targets.containsKey(variable);
    }

    boolean declaresAny(List<JCTypeParameter> typeParameters) {
        return typeParameters.stream().anyMatch(tp -> substitutes(tp.name));
    }

    List<JCTypeParameter> without(List<JCTypeParameter> typeParameters) {
        return typeParameters.stream().filter(tp -> !substitutes(tp.name)).collect(List.collector());
    }

    /** The one target, when exactly one variable is substituted. */
    Optional<TargetType> single() {
        return targets.size() == 1 ? targets.values().stream().findFirst() : Optional.empty();
    }

    boolean namesTemplate(JCTree tree) {
        return TreeUtil.flatten(tree).filter(n -> n.equals(template.simple()) || n.equals(template.qualified())).isPresent();
    }

    /** {@code Opt<T>}, {@code Opt<?>}, {@code Opt<T>[]}, {@code Map2<K, V>}: every specialized position is its own variable or {@code ?}. */
    boolean isSelfType(JCTree type) {
        return TreeUtil.asTypeApply(TreeUtil.elementType(type)).filter(ta -> namesTemplate(ta.clazz) && selectsSelf(ta.arguments)).isPresent();
    }

    boolean selectsSelf(List<JCExpression> typeArguments) {
        java.util.List<TypeParameter> parameters = template.parameters();
        return !intoItself && typeArguments.size() == parameters.size() && IntStream.range(0, parameters.size()).allMatch(i ->
                !parameters.get(i).specialized()
                        || isVariable(typeArguments.get(i), parameters.get(i).name())
                        || TreeUtil.isUnboundedWildcard(typeArguments.get(i)));
    }

    private static boolean isVariable(JCTree tree, String name) {
        return tree instanceof JCIdent id && id.name.contentEquals(name);
    }

    JCExpression selfRef(TreeMaker make, Names names) {
        return TreeUtil.packageOf(specQualified).equals(template.pkg())
                ? make.Ident(names.fromString(TreeUtil.simpleName(specQualified)))
                : TreeUtil.qualIdent(make, names, specQualified);
    }
}
