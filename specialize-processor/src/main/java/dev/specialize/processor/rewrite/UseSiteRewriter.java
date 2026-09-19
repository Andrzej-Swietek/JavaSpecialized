package dev.specialize.processor.rewrite;

import dev.specialize.processor.Diagnostics;
import dev.specialize.processor.model.Specialization;
import dev.specialize.processor.registry.ClassIndex;
import dev.specialize.processor.registry.SpecRegistry;
import dev.specialize.processor.resolve.ClassScope;
import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotatedType;
import com.sun.tools.javac.tree.JCTree.JCArrayAccess;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Optional;

/**
 * Rewrites client code before attribution: {@code Opt<Integer>} → {@code OptInt} in type positions, and the
 * expressions that must produce that type (initializers, assignments, returns, arguments, explicit type arguments).
 */
public final class UseSiteRewriter extends ScopedTranslator {
    private static final String NEEDS_TEMPLATE = "needs a @Specialize template with a specialization for that primitive";
    private static final String CONTRADICTS_BOXED = "contradicts @Boxed";

    private final TreeMaker make;
    private final Names names;
    private final ClassIndex classIndex;
    private final NameResolver resolver;
    private final Diagnostics diagnostics;
    private final ClassScope classes;
    private final SpecLookup lookup;
    private final SpecUse specs;
    private VariableScope scope = VariableScope.empty();

    public UseSiteRewriter(TreeMaker make, Names names, SpecRegistry registry, ClassIndex classIndex, NameResolver resolver,
                    Diagnostics diagnostics) {
        this.make = make;
        this.names = names;
        this.classIndex = classIndex;
        this.resolver = resolver;
        this.diagnostics = diagnostics;
        this.classes = new ClassScope(resolver);
        this.lookup = new SpecLookup(registry, classIndex, resolver, classes);
        this.specs = new SpecUse(make, names, lookup);
    }

    /** {@code Opt<int>} that did not become a specialization would silently box; the user asked for a primitive. */
    private List<JCExpression> rejectLeftoverPrimitives(JCTree clazz, List<JCExpression> typeArguments, String context) {
        typeArguments.stream().filter(TreeUtil::isMarkedPrimitive).findFirst().ifPresent(arg -> diagnostics.error(arg,
                "primitive type argument on " + TreeUtil.flatten(clazz).orElse("this type") + " " + context));
        return typeArguments.map(arg -> arg instanceof JCAnnotatedType at && TreeUtil.isMarkedPrimitive(at) ? at.underlyingType : arg);
    }

    private void withScope(VariableScope inner, Runnable action) {
        VariableScope saved = scope;
        scope = inner;
        try {
            action.run();
        } finally {
            scope = saved;
        }
    }

    @Override
    public void visitTypeApply(JCTypeApply tree) {
        if (tree.clazz instanceof JCAnnotatedType annotated && TreeUtil.hasAnnotation(annotated.annotations, TreeUtil.BOXED)) {
            tree.arguments = rejectLeftoverPrimitives(tree.clazz, translate(tree.arguments), CONTRADICTS_BOXED);
            result = tree;
            return;
        }
        super.visitTypeApply(tree);
        make.at(tree.pos);
        result = lookup.specFor(tree.clazz, tree.arguments).<JCExpression>map(spec -> specs.type(spec, tree.arguments)).orElseGet(() -> {
            tree.arguments = rejectLeftoverPrimitives(tree.clazz, tree.arguments, NEEDS_TEMPLATE);
            return tree;
        });
    }

    @Override
    public void visitAnnotatedType(JCAnnotatedType tree) {
        if (TreeUtil.hasAnnotation(tree.annotations, TreeUtil.BOXED)) {
            translateInsideOnly(tree.underlyingType);
            result = tree;
        } else {
            super.visitAnnotatedType(tree);
        }
    }

    /** {@code @Boxed}: the type arguments are still rewritten, the outer template reference stays generic. */
    private void translateInsideOnly(JCTree type) {
        TreeUtil.asTypeApply(TreeUtil.elementType(type)).ifPresent(ta ->
                ta.arguments = rejectLeftoverPrimitives(ta.clazz, translate(ta.arguments), CONTRADICTS_BOXED));
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        classes.enter(tree.name.toString());
        withScope(scope.nested(Optional.empty()), () -> {
            tree.defs.stream().filter(JCVariableDecl.class::isInstance).map(JCVariableDecl.class::cast)
                    .forEach(field -> scope.declare(field.name, fieldSpec(field))); // fields are visible before their declaration
            super.visitClassDef(tree);
        });
        classes.leave();
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        tree.mods = translate(tree.mods);
        if (TreeUtil.isBoxed(tree.mods)) {
            translateInsideOnly(tree.restype);
        } else {
            tree.restype = translate(tree.restype);
        }
        tree.typarams = translate(tree.typarams);
        tree.recvparam = translate(tree.recvparam);
        tree.params = translate(tree.params);
        tree.thrown = translate(tree.thrown);
        tree.defaultValue = translate(tree.defaultValue);
        withScope(scope.nested(Optional.ofNullable(tree.restype).flatMap(lookup::specOfType)), () -> tree.body = translate(tree.body));
        result = tree;
    }

    @Override
    public void visitVarDef(JCVariableDecl tree) {
        if (TreeUtil.isBoxed(tree.mods)) {
            tree.mods = translate(tree.mods);
            translateInsideOnly(tree.vartype);
            tree.init = translate(tree.init);
            result = tree;
            return;
        }
        super.visitVarDef(tree);
        Optional<Specialization> spec = Optional.ofNullable(tree.vartype).flatMap(lookup::specOfType);
        scope.declare(tree.name, spec);
        if (tree.init != null) {
            spec.ifPresent(s -> tree.init = specs.retarget(s, tree.init));
        }
    }

    /** The specialization a not yet rewritten field type denotes, so constructors can assign {@code this.f} before the field is visited. */
    private Optional<Specialization> fieldSpec(JCVariableDecl field) {
        return TreeUtil.isBoxed(field.mods) ? Optional.empty() : lookup.specOfParameter(TreeUtil.elementType(field.vartype), resolver);
    }

    @Override
    public void visitAssign(JCAssign tree) {
        super.visitAssign(tree);
        assignedVariable(tree.lhs).flatMap(scope::of).ifPresent(spec -> tree.rhs = specs.retarget(spec, tree.rhs));
    }

    /** {@code x}, {@code this.x} and {@code x[i]} all assign into a variable we may know the type of. */
    private Optional<Name> assignedVariable(JCExpression lhs) {
        return switch (lhs) {
            case JCIdent id -> Optional.of(id.name);
            case JCFieldAccess access when access.selected instanceof JCIdent self && self.name == names._this -> Optional.of(access.name);
            case JCArrayAccess array -> assignedVariable(array.indexed);
            default -> Optional.empty();
        };
    }

    @Override
    public void visitReturn(JCReturn tree) {
        super.visitReturn(tree);
        if (inMethodBody()) {
            scope.returnSpec().ifPresent(spec -> tree.expr = specs.retarget(spec, tree.expr));
        }
    }

    @Override
    public void visitNewClass(JCNewClass tree) {
        super.visitNewClass(tree);
        lookup.resolveOwner(TreeUtil.rawType(tree.clazz)).ifPresent(owner -> tree.args = retargeted(tree.args, owner, Optional.empty()));
        result = tree;
    }

    @Override
    public void visitApply(JCMethodInvocation tree) {
        if (!tree.typeargs.isEmpty()) {
            JCFieldAccess access = (JCFieldAccess) tree.meth; // explicit type arguments require a qualified call
            lookup.specFor(access.selected, tree.typeargs).ifPresentOrElse(spec -> {
                make.at(access.pos);
                access.selected = specs.ref(spec);
                tree.typeargs = List.from(spec.template().genericArguments(tree.typeargs));
            }, () -> tree.typeargs = rejectLeftoverPrimitives(access.selected, tree.typeargs, NEEDS_TEMPLATE));
        }
        super.visitApply(tree);
        if (tree.typeargs.isEmpty()) {
            retargetCallArguments(tree);
        }
        result = tree;
    }

    private void retargetCallArguments(JCMethodInvocation call) {
        if (call.meth instanceof JCFieldAccess access) {
            boolean receiverIsDeclaredVariable = access.selected instanceof JCIdent id && scope.holds(id.name);
            if (!receiverIsDeclaredVariable) {
                lookup.resolveOwner(access.selected).ifPresent(owner -> call.args = retargeted(call.args, owner, Optional.of(access.name.toString())));
            }
        } else {
            Name name = ((JCIdent) call.meth).name;
            Optional<String> method = name == names._this ? Optional.empty() : Optional.of(name.toString()); // this(..) targets our own constructors
            classes.enclosingClasses().stream()
                    .map(cls -> classIndex.parameterSpecs(cls, method, call.args.size(), lookup::specOfParameter))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .ifPresent(declared -> call.args = specs.retargetedArguments(call.args, declared));
        }
    }

    private List<JCExpression> retargeted(List<JCExpression> args, String owner, Optional<String> method) {
        return classIndex.parameterSpecs(owner, method, args.size(), lookup::specOfParameter)
                .map(declared -> specs.retargetedArguments(args, declared)).orElse(args);
    }
}
