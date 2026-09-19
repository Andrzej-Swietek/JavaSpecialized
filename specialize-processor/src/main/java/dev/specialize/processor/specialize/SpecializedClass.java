package dev.specialize.processor.specialize;

import dev.specialize.processor.model.TargetTuple;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The copy of a template class renamed and annotated as its specialization, before its trees are translated.
 * {@code boxOverrides}: it inherits the template's type variable from a non-template supertype, so overriding
 * methods keep the boxed signatures. {@code staticNonGeneric}: methods whose signature the substitution cannot
 * change, and which a specialized overload may therefore collide with.
 */
record SpecializedClass(JCClassDecl decl, boolean boxOverrides, Set<JCTree> staticNonGeneric) {

    static SpecializedClass prepare(JCClassDecl original, TreeMaker make, Names names, Substitution substitution,
                                    SpecializationAnnotations annotations, Predicate<JCTree> isTemplateRef) {
        JCClassDecl copy = new TreeCopier<Void>(make).copy(original);
        make.at(original.pos);
        copy.name = names.fromString(TreeUtil.simpleName(substitution.specQualified()));
        copy.typarams = substitution.without(copy.typarams);
        copy.mods.annotations = TreeUtil.without(copy.mods.annotations, Annotations.SPECIALIZE_SIMPLE)
                .append(annotations.specialized(substitution.template(), tuple(substitution, names)))
                .append(annotations.generated());
        copy.defs.stream().filter(JCClassDecl.class::isInstance).map(JCClassDecl.class::cast)
                .forEach(nested -> nested.mods.annotations = nested.mods.annotations.append(annotations.generated()));
        copy.defs = copy.defs.stream().filter(def -> !MethodSignatures.isImplicitConstructor(def)).collect(List.collector());
        Set<JCTree> staticNonGeneric = copy.defs.stream()
                .filter(def -> def instanceof JCMethodDecl m && TreeUtil.isStatic(m.mods) && !substitution.declaresAny(m.typarams))
                .collect(Collectors.toUnmodifiableSet());
        return new SpecializedClass(copy, inheritsTypeVariableFromNonTemplate(copy, substitution, isTemplateRef), staticNonGeneric);
    }

    /** The targets in the order the template declares its specialized parameters. */
    private static TargetTuple tuple(Substitution substitution, Names names) {
        return new TargetTuple(substitution.template().specialized().stream()
                .map(p -> substitution.targets().get(names.fromString(p.name()))).toList());
    }

    /** {@code class Box<T> extends Base<T>}: methods overriding {@code Base<Integer>} must keep {@code Integer} in their signatures. */
    private static boolean inheritsTypeVariableFromNonTemplate(JCClassDecl copy, Substitution substitution, Predicate<JCTree> isTemplateRef) {
        return Stream.concat(copy.implementing.stream(), Stream.ofNullable(copy.extending)).anyMatch(type ->
                substitution.targets().keySet().stream().anyMatch(tvar -> TreeUtil.mentions(type, tvar))
                        && TreeUtil.asTypeApply(type).filter(ta -> !isTemplateRef.test(ta.clazz)).isPresent());
    }
}
