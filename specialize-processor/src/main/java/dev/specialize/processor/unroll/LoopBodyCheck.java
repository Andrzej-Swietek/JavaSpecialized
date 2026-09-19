package dev.specialize.processor.unroll;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCAssignOp;
import com.sun.tools.javac.tree.JCTree.JCBreak;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCContinue;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Name;
import java.util.Optional;
import java.util.Set;

/** What stops a loop body from being copied per iteration: writes to the loop variable and jumps out of the loop. */
final class LoopBodyCheck extends TreeScanner {
    private static final Set<JCTree.Tag> INCREMENTS = Set.of(JCTree.Tag.PREINC, JCTree.Tag.POSTINC, JCTree.Tag.PREDEC, JCTree.Tag.POSTDEC);

    private final Name variable;
    private final Optional<Name> label;
    private int breakTargets;
    private int loops;
    private Optional<String> problem = Optional.empty();

    private LoopBodyCheck(Name variable, Optional<Name> label) {
        this.variable = variable;
        this.label = label;
    }

    static Optional<String> problem(JCStatement body, Name variable, Optional<Name> label) {
        LoopBodyCheck check = new LoopBodyCheck(variable, label);
        check.scan(body);
        return check.problem;
    }

    private void writes(JCTree target) {
        if (target instanceof JCTree.JCIdent id && id.name == variable) {
            report("the loop variable " + variable + " is assigned in the body");
        }
    }

    private void report(String message) {
        problem = problem.or(() -> Optional.of(message));
    }

    @Override
    public void visitAssign(JCAssign tree) {
        writes(tree.lhs);
        super.visitAssign(tree);
    }

    @Override
    public void visitAssignop(JCAssignOp tree) {
        writes(tree.lhs);
        super.visitAssignop(tree);
    }

    @Override
    public void visitUnary(JCUnary tree) {
        if (INCREMENTS.contains(tree.getTag())) {
            writes(tree.arg);
        }
        super.visitUnary(tree);
    }

    @Override
    public void visitVarDef(JCVariableDecl tree) {
        if (tree.name == variable) {
            report("the loop variable " + variable + " is redeclared in the body");
        }
        super.visitVarDef(tree);
    }

    @Override
    public void visitBreak(JCBreak tree) {
        if (tree.label == null ? breakTargets == 0 : label.filter(tree.label::equals).isPresent()) {
            report("a break leaves the loop");
        }
    }

    @Override
    public void visitContinue(JCContinue tree) {
        if (tree.label == null ? loops == 0 : label.filter(tree.label::equals).isPresent()) {
            report("a continue targets the loop");
        }
    }

    private void breakTarget(Runnable scan) {
        breakTargets++;
        scan.run();
        breakTargets--;
    }

    private void loop(Runnable scan) {
        loops++;
        breakTarget(scan);
        loops--;
    }

    @Override
    public void visitForLoop(JCForLoop tree) {
        loop(() -> super.visitForLoop(tree));
    }

    @Override
    public void visitForeachLoop(JCEnhancedForLoop tree) {
        loop(() -> super.visitForeachLoop(tree));
    }

    @Override
    public void visitWhileLoop(JCWhileLoop tree) {
        loop(() -> super.visitWhileLoop(tree));
    }

    @Override
    public void visitDoLoop(JCDoWhileLoop tree) {
        loop(() -> super.visitDoLoop(tree));
    }

    @Override
    public void visitSwitch(JCSwitch tree) {
        breakTarget(() -> super.visitSwitch(tree));
    }

    /** A lambda cannot jump out of the loop, and its parameters and locals cannot redeclare the loop variable. */
    @Override
    public void visitLambda(JCLambda tree) {
    }

    /** A class body is scanned for redeclarations; its methods' jumps are its own. */
    @Override
    public void visitClassDef(JCClassDecl tree) {
        int savedBreakTargets = breakTargets;
        int savedLoops = loops;
        breakTargets = Integer.MAX_VALUE / 2;
        loops = Integer.MAX_VALUE / 2;
        super.visitClassDef(tree);
        breakTargets = savedBreakTargets;
        loops = savedLoops;
    }
}
