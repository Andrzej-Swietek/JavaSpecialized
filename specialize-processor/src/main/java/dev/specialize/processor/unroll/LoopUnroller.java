package dev.specialize.processor.unroll;

import dev.specialize.processor.Diagnostics;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCLabeledStatement;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import java.util.Optional;

/** {@code @Unroll}: inside annotated methods, counted loops with literal bounds become one copy of the body per iteration. */
public final class LoopUnroller extends TreeTranslator {
    private static final int DEFAULT_MAX = 64;

    private final TreeMaker make;
    private final Diagnostics diagnostics;
    private Optional<Integer> max = Optional.empty();
    private int constantLoops;
    private Optional<Name> pendingLabel = Optional.empty();

    public LoopUnroller(TreeMaker make, Diagnostics diagnostics) {
        this.make = make;
        this.diagnostics = diagnostics;
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        if (!TreeUtil.hasAnnotation(tree.mods.annotations, Annotations.UNROLL_SIMPLE)) {
            super.visitMethodDef(tree);
            return;
        }
        Optional<Integer> savedMax = max;
        int savedCount = constantLoops;
        max = Optional.of(maxOf(tree));
        constantLoops = 0;
        super.visitMethodDef(tree);
        if (constantLoops == 0) {
            diagnostics.error(tree, "@Unroll " + tree.name + ": no for loop with literal bounds to unroll; write for (int i = 0; i < 8; i++) or remove @Unroll");
        }
        max = savedMax;
        constantLoops = savedCount;
    }

    private static int maxOf(JCMethodDecl method) {
        return method.mods.annotations.stream().filter(a -> TreeUtil.annotationNamed(a, Annotations.UNROLL_SIMPLE))
                .flatMap(a -> a.args.stream())
                .map(JCAssign.class::cast) // javac stops before processing when an annotation element does not exist
                .flatMap(assign -> ConstantLoop.intLiteral(assign.rhs).stream())
                .findFirst()
                .orElse(DEFAULT_MAX);
    }

    @Override
    public void visitLabelled(JCLabeledStatement tree) {
        Optional<Name> saved = pendingLabel;
        pendingLabel = Optional.of(tree.label);
        super.visitLabelled(tree);
        pendingLabel = saved;
    }

    @Override
    public void visitForLoop(JCForLoop tree) {
        Optional<Name> label = pendingLabel;
        pendingLabel = Optional.empty();
        super.visitForLoop(tree); // inner loops first
        max.ifPresent(limit -> ConstantLoop.of(tree).ifPresent(loop -> result = unroll(tree, loop, label, limit)));
    }

    private JCStatement unroll(JCForLoop tree, ConstantLoop loop, Optional<Name> label, int max) {
        constantLoops++;
        Optional<String> problem = LoopBodyCheck.problem(tree.body, loop.variable(), label);
        if (problem.isPresent()) {
            diagnostics.error(tree, "@Unroll: " + problem.get());
            return tree;
        }
        Optional<java.util.List<Integer>> values = loop.iterations(max);
        if (values.isEmpty()) {
            diagnostics.error(tree, "@Unroll: the loop runs more than " + max + " times; raise @Unroll(max = ...) or keep it a loop");
            return tree;
        }
        make.at(tree.pos);
        return make.Block(0, List.from(values.get().stream().map(value -> iteration(tree.body, loop.variable(), value)).toList()));
    }

    private JCStatement iteration(JCStatement body, Name variable, int value) {
        JCStatement copy = new TreeCopier<Void>(make).copy(body);
        return new LoopVariableSubstituter(make, variable, value).translate(copy);
    }
}
