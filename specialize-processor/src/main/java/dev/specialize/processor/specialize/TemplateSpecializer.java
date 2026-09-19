package dev.specialize.processor.specialize;

import dev.specialize.processor.model.PrimitiveTarget;
import dev.specialize.processor.model.TargetTuple;
import dev.specialize.processor.model.TargetType;
import dev.specialize.processor.model.Template;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.TreeUtil;
import dev.specialize.processor.rewrite.Retargeter;
import dev.specialize.processor.specialize.FunctionalInterfaces.Mapping;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCMemberReference;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Turns a copy of a template class into the class specialized for one tuple of targets: the specialized type
 * variables, self references, functional interfaces and {@code Prim} calls are substituted, other type variables stay.
 * {@link #translate} specializes single trees such as bridge signatures.
 */
public final class TemplateSpecializer extends TreeTranslator {
    private final TreeMaker make;
    private final Names names;
    private final Substitution substitution;
    private final SpecializationAnnotations annotations;
    private final PrimCallRewriter primCalls;
    private final Predicate<JCTree> isTemplateRef;
    private final Retargeter retargeter;
    private final BoxingDepth boxing = new BoxingDepth();
    private final TranslationScope scope = new TranslationScope();
    private boolean boxOverrides;

    /** {@code isTemplateRef}: whether a type name denotes some template (its {@code T} arguments stay primitive). */
    public TemplateSpecializer(TreeMaker make, Names names, Template template, TargetTuple tuple, String specQualified,
                        Predicate<JCTree> isTemplateRef) {
        this.make = make;
        this.names = names;
        this.substitution = Substitution.of(template, tuple, specQualified, names);
        this.annotations = new SpecializationAnnotations(make, names);
        this.primCalls = new PrimCallRewriter(make, names, substitution);
        this.isTemplateRef = isTemplateRef;
        boolean keepsTypeParameters = !template.genericParameterNames().isEmpty();
        this.retargeter = new Retargeter(make, substitution::namesTemplate, _ -> false, this::selfRef, keepsTypeParameters);
    }

    /** A fresh, fully specialized copy of the template class. */
    public JCClassDecl specialize(JCClassDecl original) {
        SpecializedClass prepared = SpecializedClass.prepare(original, make, names, substitution, annotations, isTemplateRef);
        JCClassDecl copy = prepared.decl();
        boxOverrides = prepared.boxOverrides();
        copy.defs.stream().filter(JCVariableDecl.class::isInstance).map(JCVariableDecl.class::cast).forEach(this::declareField);
        scope.inSupertypes(() -> {
            copy.extending = translate(copy.extending);
            copy.implementing = translate(copy.implementing);
        });
        copy.defs = translate(copy.defs);
        copy.defs = MethodSignatures.withoutColliding(copy.defs, prepared.staticNonGeneric());
        return copy;
    }

    private void declareField(JCVariableDecl field) {
        scope.declareField(field.name, functionalMapping(field.vartype), substitution.valueTarget(field.vartype));
    }

    private JCExpression selfRef() {
        return substitution.selfRef(make, names);
    }

    private Optional<TargetType> targetOf(JCTree tree) {
        return scope.shadowed() ? Optional.empty() : substitution.targetOf(tree);
    }

    private boolean isT(JCTree tree) {
        return targetOf(tree).isPresent();
    }

    private Optional<PrimitiveTarget> primitiveOf(JCTree tree) {
        return targetOf(tree).filter(PrimitiveTarget.class::isInstance).map(PrimitiveTarget.class::cast);
    }

    private boolean isSelfType(JCTree type) {
        return !scope.shadowed() && substitution.isSelfType(type);
    }

    /** {@code Supplier<T>} as a supertype stays {@code Supplier<Integer>}: its {@code get()} is an override, not a field to unbox. */
    private Optional<Mapping> functionalMapping(JCTree type) {
        return scope.inSupertypes() ? Optional.empty() : FunctionalInterfaces.kindOf(type, this::primitiveOf);
    }

    /** {@code int} at a value position, the box as a type argument, marked "was primitive" inside other templates. */
    private JCExpression typeFor(TargetType target) {
        JCExpression boxed = TreeUtil.qualIdent(make, names, target.boxed());
        return switch (target) {
            case PrimitiveTarget p when boxing.valuePosition() -> make.TypeIdent(p.tag());
            case PrimitiveTarget _ when boxing.marking() -> make.AnnotatedType(
                    List.of(make.Annotation(TreeUtil.qualIdent(make, names, Annotations.PRIMITIVE_ARGUMENT), List.nil())), boxed);
            default -> boxed;
        };
    }

    @Override
    public void visitIdent(JCIdent tree) {
        make.at(tree.pos);
        result = targetOf(tree).<JCTree>map(this::typeFor).orElse(tree);
    }

    /** A nested class declaring its own {@code T} shadows the template's; nothing inside it is substituted. */
    @Override
    public void visitClassDef(JCClassDecl tree) {
        scope.withShadowing(substitution.declaresAny(tree.typarams), () -> super.visitClassDef(tree));
    }

    @Override
    public void visitTypeApply(JCTypeApply tree) {
        make.at(tree.pos);
        if (isSelfType(tree)) {
            result = selfType(tree);
            return;
        }
        Optional<Mapping> mapping = functionalMapping(tree);
        if (mapping.isPresent()) {
            result = FunctionalInterfaces.mapped(mapping.get(), tree, make, names, this::translateBoxed);
            return;
        }
        tree.arguments = boxing.boxed(isTemplateRef.test(tree.clazz), () -> translate(tree.arguments));
        result = tree;
    }

    private <T extends JCTree> T translateBoxed(T tree) {
        return boxing.boxed(boxing.marking(), () -> translate(tree));
    }

    /** {@code Map2<K, V>} → {@code Map2Int<V>}: the generic positions keep their (translated) arguments. */
    private JCExpression selfType(JCTypeApply tree) {
        List<JCExpression> kept = List.from(substitution.template().genericArguments(tree.arguments));
        return kept.isEmpty() ? selfRef() : make.TypeApply(selfRef(), boxing.boxed(isTemplateRef.test(tree.clazz), () -> translate(kept)));
    }

    @Override
    public void visitTypeParameter(JCTypeParameter tree) {
        boxing.boxed(boxing.marking(), () -> {
            super.visitTypeParameter(tree);
            return null;
        });
    }

    @Override
    public void visitTypeArray(JCArrayTypeTree tree) {
        tree.elemtype = boxing.atValue(() -> translate(tree.elemtype));
        result = tree;
    }

    @Override
    public void visitTypeCast(JCTypeCast tree) {
        tree.clazz = isT(tree.clazz) ? translateBoxed(tree.clazz) : translate(tree.clazz);
        tree.expr = translate(tree.expr);
        result = tree;
    }

    /**
     * A static {@code <T>} is the template's type variable by convention (statics cannot use the class one);
     * an instance method declaring {@code <T>} shadows it and keeps its own type variable.
     */
    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        boolean declaresT = substitution.declaresAny(tree.typarams);
        boolean isStatic = TreeUtil.isStatic(tree.mods);
        if (isStatic) {
            tree.typarams = substitution.without(tree.typarams);
        }
        scope.inMethod(declaresT && !isStatic, () -> tree.restype != null && isSelfType(tree.restype), () -> {
            if (boxOverrides && TreeUtil.hasAnnotation(tree.mods.annotations, Annotations.OVERRIDE_SIMPLE)) {
                visitOverride(tree);
            } else {
                super.visitMethodDef(tree);
            }
        });
    }

    /** Signature in boxed form (it overrides a supertype instantiated with the box), body specialized as usual. */
    private void visitOverride(JCMethodDecl tree) {
        tree.mods = translate(tree.mods);
        tree.typarams = translate(tree.typarams);
        tree.restype = boxing.boxed(false, () -> translate(tree.restype));
        tree.params.forEach(param -> param.vartype = boxing.boxed(false, () -> translate(param.vartype)));
        tree.thrown = translate(tree.thrown);
        tree.body = translate(tree.body);
        result = tree;
    }

    @Override
    public void visitVarDef(JCVariableDecl tree) {
        if (tree.vartype == null) {
            tree.vartype = make.at(tree.pos).Ident(names.var);
        }
        boolean self = isSelfType(tree.vartype);
        Optional<Mapping> mapping = functionalMapping(tree.vartype);
        scope.declare(tree.name, substitution.valueTarget(tree.vartype));
        super.visitVarDef(tree);
        scope.map(tree.name, mapping);
        if (self && tree.init != null) {
            tree.init = retargeter.retarget(tree.init);
        }
    }

    @Override
    public void visitReturn(JCReturn tree) {
        super.visitReturn(tree);
        if (scope.returnsSelfHere()) {
            tree.expr = retargeter.retarget(tree.expr);
        }
    }

    @Override
    public void visitLambda(JCLambda tree) {
        scope.inLambda(() -> super.visitLambda(tree));
    }

    @Override
    public void visitApply(JCMethodInvocation tree) {
        make.at(tree.pos);
        Optional<TargetType> explicitTarget = tree.typeargs.size() == 1 ? targetOf(tree.typeargs.head) : Optional.empty();
        Optional<JCFieldAccess> qualified = tree.meth instanceof JCFieldAccess access ? Optional.of(access) : Optional.empty();
        qualified.filter(access -> substitution.namesTemplate(access.selected) && !scope.shadowed() && substitution.selectsSelf(tree.typeargs))
                .ifPresent(access -> {
                    access.selected = selfRef();
                    tree.typeargs = List.from(substitution.template().genericArguments(tree.typeargs));
                });
        boolean templateReceiver = qualified.filter(access -> isTemplateRef.test(access.selected)).isPresent();
        tree.typeargs = boxing.boxed(templateReceiver, () -> translate(tree.typeargs));
        tree.meth = translate(tree.meth);
        tree.args = translate(tree.args);
        result = primCalls.rewrite(tree, explicitTarget, scope.declared()).orElseGet(() -> renameFunctionalCall(tree));
    }

    private JCExpression renameFunctionalCall(JCMethodInvocation call) {
        if (call.meth instanceof JCFieldAccess access) {
            mappedReceiver(access.selected).flatMap(mapping -> mapping.renamed(access.name, names)).ifPresent(renamed -> access.name = renamed);
        }
        return call;
    }

    /** {@code sup} when that name was mapped in this method; {@code this.sup} or {@code other.sup} when the field was. */
    private Optional<Mapping> mappedReceiver(JCExpression receiver) {
        return switch (receiver) {
            case JCIdent id -> scope.mappedVar(id.name);
            case JCFieldAccess access when access.selected instanceof JCIdent -> scope.mappedField(access.name);
            default -> Optional.empty();
        };
    }

    @Override
    public void visitReference(JCMemberReference tree) {
        super.visitReference(tree);
        mappedReceiver(tree.expr).flatMap(mapping -> mapping.renamed(tree.name, names)).ifPresent(renamed -> tree.name = renamed);
        result = tree;
    }
}
