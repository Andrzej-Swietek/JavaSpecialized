package dev.specialize.processor.rewrite;

import dev.specialize.processor.model.Specialization;
import dev.specialize.processor.registry.ClassIndex;
import dev.specialize.processor.registry.SpecRegistry;
import dev.specialize.processor.resolve.ClassScope;
import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Which specialization a type tree, a parameter or a lambda's result denotes, seen from one compilation unit. */
record SpecLookup(SpecRegistry registry, ClassIndex classIndex, NameResolver resolver, ClassScope scope) {
    /** Functional interfaces whose last type argument is what a lambda assigned to them produces. */
    private static final Set<String> RESULT_LAST = Set.of(
            "Supplier", "Callable", "Function", "BiFunction", "UnaryOperator", "BinaryOperator",
            "IntFunction", "LongFunction", "DoubleFunction");

    /** {@code Opt<Integer>} / {@code Opt<int>} / {@code List<int>} as written in this unit. */
    Optional<Specialization> specFor(JCExpression clazz, List<JCExpression> typeArguments) {
        return specFor(resolver, clazz, typeArguments);
    }

    Optional<Specialization> specFor(NameResolver owner, JCExpression clazz, List<JCExpression> typeArguments) {
        return owner.resolveTypeName(clazz).flatMap(name -> registry.template(name)
                .flatMap(template -> template.tupleFor(typeArguments, owner, registry.isAlias(name))
                        .flatMap(tuple -> registry.specializationFor(template, tuple))));
    }

    /** The specialization an already rewritten type tree denotes: the type itself, or the result of a lambda assigned to it. */
    Optional<Specialization> specOfType(JCTree type) {
        JCTree element = TreeUtil.elementType(type);
        JCTree named = TreeUtil.rawType(element);
        return TreeUtil.flatten(named).flatMap(name -> registry.specByName(name)
                        .or(() -> resolver.resolveTypeName(named).filter(q -> !q.equals(name)).flatMap(registry::specByName)))
                .or(() -> lambdaResultSpec(element, this::specOfType));
    }

    private static Optional<Specialization> lambdaResultSpec(JCTree type, Function<JCTree, Optional<Specialization>> specOf) {
        return TreeUtil.asTypeApply(type)
                .filter(ta -> TreeUtil.flatten(ta.clazz).map(TreeUtil::simpleName).filter(RESULT_LAST::contains).isPresent())
                .flatMap(ta -> specOf.apply(ta.arguments.last()));
    }

    /** The specialization a parameter type in another class denotes, resolved with that class's own imports. */
    Optional<Specialization> specOfParameter(JCTree type, NameResolver owner) {
        return TreeUtil.asTypeApply(type)
                .map(ta -> specFor(owner, ta.clazz, ta.arguments).or(() -> lambdaResultSpec(ta, t -> specOfParameter(t, owner))))
                .orElseGet(() -> owner.resolveTypeName(type).flatMap(registry::specByName));
    }

    /** A class named as the resolver sees it, or a nested class of an enclosing class. */
    Optional<String> resolveOwner(JCTree clazz) {
        return resolver.resolveTypeName(clazz).or(() -> clazz instanceof JCIdent id
                ? scope.enclosingClasses().stream().map(cls -> cls + "." + id.name).filter(classIndex::contains).findFirst()
                : Optional.empty());
    }

    /** {@code some(..)} statically imported from the template, unless a method of an enclosing class shadows it (JLS 6.4.1). */
    boolean callsTemplateFactory(String name, String template) {
        boolean imported = resolver.staticImportOwner(name).filter(template::equals).isPresent() || resolver.staticOnDemandOwners().contains(template);
        return imported && scope.enclosingClasses().stream().noneMatch(cls -> classIndex.declaresMethod(cls, name));
    }

    /** Whether a type tree names {@code template} directly or through one of its aliases. */
    boolean namesTemplate(JCTree tree, String template) {
        return resolver.resolveTypeName(tree).flatMap(registry::templateNameFor).filter(template::equals).isPresent();
    }
}
