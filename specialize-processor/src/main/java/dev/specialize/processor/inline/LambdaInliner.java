package dev.specialize.processor.inline;

import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A lambda passed to an {@code @Inline} method, applied where the body calls it: {@code f.applyAsInt(items[i])} with
 * {@code x -> x * 2} becomes {@code switch (0) { default -> { var x$l1 = items[i]; yield x$l1 * 2; } }}, or plainly
 * {@code (v * 2)} when the argument is pure.
 */
final class LambdaInliner {
    private static final String TAG = "$l";

    /** One lambda parameter bound to its argument: directly, or through a temporary local. */
    private record Bound(Name parameter, JCExpression replacement, Optional<JCStatement> temporary) {
        static Bound of(JCVariableDecl param, JCExpression arg, int group, TreeMaker make, Names names) {
            if (TreeUtil.isPure(arg)) {
                return new Bound(param.name, TreeUtil.parenthesized(make, arg), Optional.empty());
            }
            Name temporary = names.fromString(param.name + TAG + group);
            // an implicitly typed lambda parameter has no vartype (javac: null) and becomes a 'var' local
            return new Bound(param.name, make.Ident(temporary), Optional.of(make.VarDef(make.Modifiers(0), temporary, param.vartype, arg)));
        }
    }

    private final TreeMaker make;
    private final JCLambda lambda;
    private final List<JCStatement> bindings;
    private final Map<Name, JCExpression> substitution;
    private final Map<Name, Name> renamedLocals;

    private LambdaInliner(TreeMaker make, Names names, JCLambda lambda, List<JCExpression> args, int group) {
        this.make = make;
        this.lambda = lambda;
        java.util.List<Bound> bound = IntStream.range(0, lambda.params.size())
                .mapToObj(i -> Bound.of(lambda.params.get(i), args.get(i), group, make, names))
                .toList();
        this.substitution = bound.stream().collect(Collectors.toUnmodifiableMap(Bound::parameter, Bound::replacement));
        this.bindings = bound.stream().flatMap(b -> b.temporary().stream()).collect(List.collector());
        this.renamedLocals = InlinedBlocks.renamedLocals(lambda.body, names, TAG, group);
    }

    /** The lambda behind {@code ((IntUnaryOperator) (x -> …))}, as the receiver of a call inside an inlined body. */
    static Optional<JCLambda> lambdaOf(JCExpression receiver) {
        return switch (receiver) {
            case JCParens parens -> lambdaOf(parens.expr);
            case JCTypeCast cast -> lambdaOf(cast.expr);
            case JCLambda lambda -> Optional.of(lambda);
            default -> Optional.empty();
        };
    }

    /** {@code f.applyAsInt(args)} as an expression; empty when the lambda body has no single result. */
    static Optional<JCExpression> asExpression(JCLambda lambda, List<JCExpression> args, TreeMaker make, Names names, LocalNames locals) {
        return new LambdaInliner(make, names, lambda, args, locals.nextGroup()).expression();
    }

    /** {@code f.accept(args);} as a statement; empty when the body returns or is not a statement expression. */
    static Optional<JCStatement> asStatement(JCLambda lambda, List<JCExpression> args, TreeMaker make, Names names, LocalNames locals) {
        return new LambdaInliner(make, names, lambda, args, locals.nextGroup()).statement();
    }

    private Optional<JCExpression> expression() {
        if (lambda.body instanceof JCExpression value) {
            JCExpression bound = substituted(value);
            return Optional.of(bindings.isEmpty() ? make.Parens(bound) : InlinedBlocks.switchExpression(bindings.append(make.Yield(bound)), make));
        }
        JCBlock block = (JCBlock) lambda.body; // value-compatible, so every return carries a value
        return block.stats.last() instanceof JCReturn
                ? Optional.of(InlinedBlocks.switchExpression(bindings.appendList(substituted(block).stats), make))
                : Optional.empty();
    }

    private Optional<JCStatement> statement() {
        if (lambda.body instanceof JCExpression value) {
            return TreeInfo.isExpressionStatement(value)
                    ? Optional.of(make.Block(0, bindings.append(make.Exec(substituted(value)))))
                    : Optional.empty();
        }
        JCBlock block = (JCBlock) lambda.body;
        return InlinedBlocks.returns(block) ? Optional.empty() : Optional.of(make.Block(0, bindings.appendList(substituted(block).stats)));
    }

    private <T extends JCTree> T substituted(T tree) {
        return InlinedBlocks.substituted(new com.sun.tools.javac.tree.TreeCopier<Void>(make).copy(tree), substitution, renamedLocals, make);
    }
}
