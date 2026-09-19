package dev.specialize.processor.inline;

import dev.specialize.processor.registry.ClassIndex;
import dev.specialize.processor.resolve.Match;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import java.util.Optional;
import java.util.stream.Stream;

/** The shape an {@code @Inline} method must have. */
final class InlineBodyValidator {
    private InlineBodyValidator() {
    }

    /** The reason a method cannot be inlined; empty when it can. */
    static Optional<String> problem(JCMethodDecl method) {
        boolean hasBody = method.body != null && method.body.stats.nonEmpty();
        boolean isVoid = TreeUtil.isVoid(method.restype);
        return Stream.of(
                        check(!TreeUtil.isStatic(method.mods), "only static methods can be inlined"),
                        check(ClassIndex.isVarargs(method), "varargs methods cannot be inlined"),
                        check(!hasBody, "the body must have at least one statement"),
                        check(hasBody && containsLambdaOrAnonymousClass(method.body), "bodies containing lambdas or anonymous classes cannot be inlined"),
                        check(hasBody && isVoid && singleExpression(method).isEmpty() && InlinedBlocks.returns(method.body),
                                "a void block body must not contain 'return' (it would return from the caller)"),
                        check(hasBody && !isVoid && !(method.body.stats.last() instanceof JCReturn), "a block body must end with 'return <expression>;'"))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> check(boolean failed, String message) {
        return failed ? Optional.of(message) : Optional.empty();
    }

    /** Requires a non-empty body. */
    static Optional<JCExpression> singleExpression(JCMethodDecl method) {
        if (method.body.stats.size() != 1) {
            return Optional.empty();
        }
        return switch (method.body.stats.head) {
            case JCReturn r -> Optional.of(r.expr);
            case JCExpressionStatement es -> Optional.of(es.expr);
            default -> Optional.empty();
        };
    }

    private static boolean containsLambdaOrAnonymousClass(JCTree tree) {
        return Match.in(tree, new Match() {
            @Override
            public void visitLambda(JCLambda lambda) {
                found = true;
            }

            @Override
            public void visitNewClass(JCNewClass creation) {
                found |= creation.def != null;
                super.visitNewClass(creation);
            }
        });
    }
}
