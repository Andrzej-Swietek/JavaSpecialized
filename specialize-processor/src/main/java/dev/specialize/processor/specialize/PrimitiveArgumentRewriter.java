package dev.specialize.processor.specialize;

import dev.specialize.processor.model.PrimitiveTarget;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.JCTree.JCAnnotatedType;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import javax.tools.Diagnostic;

/** {@code Opt<int>} → {@code Opt<@PrimitiveArgument java.lang.Integer>}, applied to every compilation unit right after parsing. */
public final class PrimitiveArgumentRewriter extends TreeTranslator {
    private final TreeMaker make;
    private final Names names;

    public PrimitiveArgumentRewriter(TreeMaker make, Names names) {
        this.make = make;
        this.names = names;
    }

    public static void install(JavacTask task) {
        Context context = ((BasicJavacTask) task).getContext();
        PrimitiveArgumentRewriter rewriter = new PrimitiveArgumentRewriter(TreeMaker.instance(context), Names.instance(context));
        JavacTrees trees = JavacTrees.instance(context);
        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent event) {
                if (event.getKind() == TaskEvent.Kind.PARSE) {
                    rewriter.translate((JCCompilationUnit) event.getCompilationUnit());
                }
            }

            @Override
            public void started(TaskEvent event) {
                if (event.getKind() == TaskEvent.Kind.ANALYZE) {
                    rejectUnprocessedMarkers(trees, (JCCompilationUnit) event.getCompilationUnit());
                }
            }
        });
    }

    /** Attribution starts after processing: a marker still present means no processor turned it into a specialization. */
    private static void rejectUnprocessedMarkers(JavacTrees trees, JCCompilationUnit unit) {
        new TreeScanner() {
            @Override
            public void visitAnnotatedType(JCAnnotatedType tree) {
                if (TreeUtil.hasAnnotation(tree.annotations, Annotations.PRIMITIVE_ARGUMENT_SIMPLE)) {
                    trees.printMessage(Diagnostic.Kind.ERROR, "primitive type argument was not rewritten: the specialize annotation "
                            + "processor did not run, so " + TreeUtil.flatten(tree.underlyingType).orElse("this type")
                            + " would be compiled boxed; enable annotation processing or add specialize-processor to the processor path",
                            tree, unit);
                }
                super.visitAnnotatedType(tree);
            }
        }.scan(unit);
    }

    @Override
    public void visitTypeApply(JCTypeApply tree) {
        super.visitTypeApply(tree);
        tree.arguments = tree.arguments.map(this::markPrimitive);
        result = tree;
    }

    @Override
    public void visitApply(JCMethodInvocation tree) {
        super.visitApply(tree);
        tree.typeargs = tree.typeargs.map(this::markPrimitive);
        result = tree;
    }

    private JCExpression markPrimitive(JCExpression argument) {
        return argument instanceof JCPrimitiveTypeTree primitive
                ? PrimitiveTarget.of(primitive.typetag).<JCExpression>map(target -> marked(argument.pos, target)).orElse(argument)
                : argument;
    }

    private JCExpression marked(int pos, PrimitiveTarget target) {
        make.at(pos);
        return make.AnnotatedType(List.of(make.Annotation(TreeUtil.qualIdent(make, names, Annotations.PRIMITIVE_ARGUMENT), List.nil())),
                TreeUtil.qualIdent(make, names, target.boxed()));
    }
}
