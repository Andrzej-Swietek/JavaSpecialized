package dev.specialize.processor.specialize;

import dev.specialize.BoxedArguments;
import dev.specialize.processor.model.BinaryTemplate;
import dev.specialize.processor.model.NamePattern;
import dev.specialize.processor.model.TargetTuple;
import dev.specialize.processor.model.TargetType;
import dev.specialize.processor.model.Template.TypeParameter;
import dev.specialize.processor.registry.AnnotationReader.MethodSpecialization;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@code @Specialize} on a static generic method: adds one overload per type next to it, with {@code T} substituted
 * the same way a template class is ({@code Prim} helpers, functional interfaces, {@code T[]}).
 */
public final class MethodSpecializer {
    private final TreeMaker make;
    private final Names names;
    private final Predicate<JCTree> isTemplateRef;

    public MethodSpecializer(TreeMaker make, Names names, Predicate<JCTree> isTemplateRef) {
        this.make = make;
        this.names = names;
        this.isTemplateRef = isTemplateRef;
    }

    public void specialize(JCClassDecl owner, JCMethodDecl method, MethodSpecialization request) {
        TypeParameter parameter = TypeParameter.specialized(request.typeVariable(), request.types());
        BinaryTemplate synthetic = new BinaryTemplate(request.ownerQualified(), request.namePattern(), java.util.List.of(parameter),
                BinaryTemplate.product(java.util.List.of(parameter)), false, BoxedArguments.SPECIALIZE, Optional.empty());
        Name variable = names.fromString(request.typeVariable());
        boolean overloadable = method.params.stream().anyMatch(p -> isVariableOrArray(p.vartype, variable));
        owner.defs = owner.defs.appendList(request.types().stream()
                .map(target -> overload(method, synthetic, target, overloadable))
                .collect(List.collector()));
    }

    private JCMethodDecl overload(JCMethodDecl method, BinaryTemplate synthetic, TargetType target, boolean overloadable) {
        TemplateSpecializer specializer = new TemplateSpecializer(make, names, synthetic, TargetTuple.of(target), synthetic.qualified(), isTemplateRef);
        make.at(method.pos);
        JCMethodDecl copy = specializer.translate(new TreeCopier<Void>(make).copy(method));
        copy.mods.annotations = TreeUtil.without(copy.mods.annotations, Annotations.SPECIALIZE_SIMPLE);
        if (!overloadable) {
            copy.name = names.fromString(NamePattern.expand(synthetic.namePattern(), method.name.toString(), target.suffix()));
        }
        return copy;
    }

    /** {@code T} or {@code T[]}: a parameter whose erasure changes; such overloads keep the method's name. */
    private static boolean isVariableOrArray(JCTree type, Name variable) {
        return switch (type) {
            case JCArrayTypeTree array -> isVariableOrArray(array.elemtype, variable);
            case JCIdent id -> id.name == variable;
            default -> false;
        };
    }
}
