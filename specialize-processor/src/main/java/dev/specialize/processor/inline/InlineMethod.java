package dev.specialize.processor.inline;

import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import java.util.Optional;
import java.util.stream.IntStream;

/**
 * One {@code @Inline} method: its fully qualified body, an expression or a block, and the parameter types arguments
 * are cast to ({@code type} is empty when the declared type mentions a type variable, so no cast is inserted). A block
 * body is inlined as a {@code switch (0) { default -> { …; yield result; } }} expression with its locals renamed.
 */
public record InlineMethod(
        String owner,
        String name,
        java.util.List<Parameter> parameters,
        Optional<JCExpression> returnType,
        boolean isVoid,
        JCTree body
) {
    public record Parameter(String name, Optional<JCExpression> type) {
    }

    public boolean isBlock() {
        return body instanceof JCBlock;
    }

    /**
     * The call replaced by the body as an expression; empty when an argument is impure, or a lambda whose parameter has no declared type.
     */
    public Optional<JCExpression> expand(List<JCExpression> args, TreeMaker make, Names names, LocalNames locals) {
        if (!acceptable(args)) {
            return Optional.empty();
        }
        JCTree substituted = new BodySubstitution(
                this,
                args,
                make,
                names,
                locals
        ).apply(new TreeCopier<Void>(make).copy(body));
        JCExpression expression = substituted instanceof JCBlock block ? InlinedBlocks.switchExpression(block.stats, make) : (JCExpression) substituted;
        if (isVoid) {
            return Optional.of(expression);
        }
        return Optional.of(make.Parens(returnType.<JCExpression>map(type -> make.TypeCast(new TreeCopier<Void>(make).copy(type), make.Parens(expression)))
                .orElse(expression)));
    }

    /**
     * A {@code void} body replacing the call statement: the block itself, or the expression as a statement.
     */
    public Optional<JCStatement> expandStatement(List<JCExpression> args, TreeMaker make, Names names, LocalNames locals) {
        if (!acceptable(args)) {
            return Optional.empty();
        }
        TreeCopier<Void> copier = new TreeCopier<>(make);
        JCTree root = body instanceof JCExpression expression ? make.Exec(copier.copy(expression)) : copier.copy(body);
        return Optional.of((JCStatement) new BodySubstitution(this, args, make, names, locals).apply(root));
    }

    /**
     * Pure arguments only, or a lambda for a parameter with a declared type; anything else could run twice, never, or out of order.
     */
    private boolean acceptable(List<JCExpression> args) {
        return IntStream.range(0, args.size()).allMatch(i -> TreeUtil.isPure(args.get(i))
                || args.get(i) instanceof JCLambda && parameters.get(i).type().isPresent());
    }
}
