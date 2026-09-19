package dev.specialize.processor.inline;

import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * An inline body with its parameters replaced by the call's arguments, its locals renamed apart, and lambda
 * arguments applied where the body calls them.
 */
final class BodySubstitution extends TreeTranslator {
    private static final String TAG = "$inl";

    private final java.util.List<InlineMethod.Parameter> parameters;
    private final List<JCExpression> args;
    private final Map<Name, Integer> index;
    private final Map<Name, Name> renamedLocals;
    private final TreeCopier<Void> copier;
    private final TreeMaker make;
    private final Names names;
    private final LocalNames locals;

    BodySubstitution(InlineMethod method, List<JCExpression> args, TreeMaker make, Names names, LocalNames locals) {
        this.parameters = method.parameters();
        this.args = args;
        this.index = IntStream.range(0, parameters.size()).boxed()
                .collect(Collectors.toMap(i -> names.fromString(parameters.get(i).name()), i -> i));
        this.renamedLocals = InlinedBlocks.renamedLocals(method.body(), names, TAG, locals.nextGroup());
        this.copier = new TreeCopier<>(make);
        this.make = make;
        this.names = names;
        this.locals = locals;
    }

    @Override
    public void visitIdent(JCIdent ident) {
        result = Optional.ofNullable(index.get(ident.name))
                .map(i -> argument(i, copier.copy(args.get(i))))
                .or(() -> Optional.ofNullable(renamedLocals.get(ident.name)).map(renamed -> make.at(ident.pos).Ident(renamed)))
                .orElse(ident);
    }

    @Override
    public void visitVarDef(JCVariableDecl local) {
        super.visitVarDef(local);
        local.name = renamedLocals.getOrDefault(local.name, local.name);
        result = local;
    }

    /** {@code f.accept(v);} with a lambda for {@code f}: the lambda's statements, in a block. */
    @Override
    public void visitExec(JCExpressionStatement statement) {
        Optional<JCMethodInvocation> call = statement.expr instanceof JCMethodInvocation c ? Optional.of(c) : Optional.empty();
        Optional<JCLambda> lambda = call.flatMap(c -> lambdaArgument(c.meth));
        if (lambda.isEmpty()) {
            super.visitExec(statement);
            return;
        }
        JCMethodInvocation c = call.orElseThrow();
        List<JCExpression> callArgs = translate(c.args);
        Optional<JCStatement> inlined = LambdaInliner.asStatement(copier.copy(lambda.get()), callArgs, make.at(statement.pos), names, locals);
        if (inlined.isPresent()) {
            result = inlined.get();
            return;
        }
        c.args = callArgs; // a switch expression cannot stand as a statement: keep the call on the cast lambda
        c.meth = translate(c.meth);
        result = statement;
    }

    /** {@code f.applyAsInt(v)} with a lambda for {@code f}: the lambda's result expression. */
    @Override
    public void visitApply(JCMethodInvocation call) {
        super.visitApply(call); // bodies are stored fully qualified, so every call here is a field access
        result = call;
        LambdaInliner.lambdaOf(((JCFieldAccess) call.meth).selected)
                .flatMap(lambda -> LambdaInliner.asExpression(lambda, call.args, make.at(call.pos), names, locals))
                .ifPresent(expression -> result = expression);
    }

    /** The lambda bound to the parameter a call is made on, if the receiver is such a parameter. */
    private Optional<JCLambda> lambdaArgument(JCExpression method) {
        return Optional.of(method).filter(JCFieldAccess.class::isInstance).map(m -> ((JCFieldAccess) m).selected)
                .filter(JCIdent.class::isInstance).map(id -> index.get(((JCIdent) id).name))
                .map(args::get).filter(JCLambda.class::isInstance).map(JCLambda.class::cast);
    }

    /** The argument in the parameter's declared type: {@code (int) (expr)}. */
    private JCExpression argument(int i, JCExpression arg) {
        JCExpression safeArg = TreeUtil.parenthesized(make, arg);
        return parameters.get(i).type()
                .map(type -> (JCExpression) make.Parens(make.TypeCast(copier.copy(type), safeArg)))
                .orElse(safeArg);
    }

    JCTree apply(JCTree root) {
        return translate(root);
    }
}
