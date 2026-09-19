package dev.specialize.processor.unroll;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Name;

/** Replaces every read of the loop variable with the value it has in one iteration. */
final class LoopVariableSubstituter extends TreeTranslator {
    private final TreeMaker make;
    private final Name variable;
    private final int value;

    LoopVariableSubstituter(TreeMaker make, Name variable, int value) {
        this.make = make;
        this.variable = variable;
        this.value = value;
    }

    @Override
    public void visitIdent(JCIdent tree) {
        result = tree.name == variable ? make.at(tree.pos).Literal(TypeTag.INT, value) : tree;
    }

    /** {@code i(x)} calls a method named like the variable; only the arguments are values. */
    @Override
    public void visitApply(JCMethodInvocation tree) {
        tree.typeargs = translate(tree.typeargs);
        tree.meth = tree.meth instanceof JCIdent ? tree.meth : translate(tree.meth);
        tree.args = translate(tree.args);
        result = tree;
    }
}
