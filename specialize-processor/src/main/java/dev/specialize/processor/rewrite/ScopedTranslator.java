package dev.specialize.processor.rewrite;

import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.TreeTranslator;

/** A translator that knows whether it is inside a lambda body, whose {@code return}s are the lambda's, not the method's. */
public abstract class ScopedTranslator extends TreeTranslator {
    private int lambdaDepth;

    protected boolean inMethodBody() {
        return lambdaDepth == 0;
    }

    @Override
    public void visitLambda(JCLambda tree) {
        lambdaDepth++;
        super.visitLambda(tree);
        lambdaDepth--;
    }
}
