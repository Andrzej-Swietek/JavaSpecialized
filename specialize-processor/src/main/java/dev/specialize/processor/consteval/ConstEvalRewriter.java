package dev.specialize.processor.consteval;

import dev.specialize.processor.Diagnostics;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.consteval.EvaluationResult.Failure;
import dev.specialize.processor.consteval.EvaluationResult.Value;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Names;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@code @ConstEval} fields and {@code Const.eval(expr)} calls. The visit collects them; a call's expression is
 * spliced into the source text as a hidden static field of its class (on the line of the opening brace, so line
 * numbers stay true). {@link #resolve()} compiles that text once on the side, reads the values and places the literals.
 */
public final class ConstEvalRewriter extends TreeTranslator {
    /** One value to compute: where it lives in the side compilation, where its literal goes, how to report. */
    private record Request(String binaryName, String field, JCTree at, String label, Consumer<JCExpression> place) {
    }

    /** A hidden field to splice in right after a class's opening brace. */
    private record Splice(int classPos, String declaration) {
    }

    /** An enclosing class; the binary name is empty for local and anonymous classes, which cannot be loaded by name. */
    private record Enclosing(JCClassDecl decl, Optional<String> binaryName) {
        boolean isInterface() {
            return (decl.mods.flags & Flags.INTERFACE) != 0;
        }

        boolean isEnum() {
            return (decl.mods.flags & Flags.ENUM) != 0;
        }
    }

    private final TreeMaker make;
    private final Names names;
    private final JCCompilationUnit unit;
    private final ConstantEvaluator evaluator;
    private final Diagnostics diagnostics;
    private final Deque<Enclosing> enclosing = new ArrayDeque<>();
    private final java.util.List<Request> requests = new ArrayList<>();
    private final java.util.List<Splice> splices = new ArrayList<>();
    private int localDepth;

    public ConstEvalRewriter(TreeMaker make, Names names, JCCompilationUnit unit, ConstantEvaluator evaluator, Diagnostics diagnostics) {
        this.make = make;
        this.names = names;
        this.unit = unit;
        this.evaluator = evaluator;
        this.diagnostics = diagnostics;
    }


    @Override
    public void visitClassDef(JCClassDecl tree) {
        enclosing.addLast(new Enclosing(tree, binaryName(tree)));
        super.visitClassDef(tree);
        enclosing.removeLast();
    }

    private Optional<String> binaryName(JCClassDecl tree) {
        if (tree.name.isEmpty() || localDepth > 0) {
            return Optional.empty();
        }
        String pkg = Optional.ofNullable(unit.getPackageName()).map(p -> p + ".").orElse("");
        return enclosing.isEmpty() ? Optional.of(pkg + tree.name) : enclosing.peekLast().binaryName().map(outer -> outer + "$" + tree.name);
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        local(() -> super.visitMethodDef(tree));
    }

    @Override
    public void visitBlock(JCBlock tree) {
        local(() -> super.visitBlock(tree));
    }

    @Override
    public void visitLambda(JCLambda tree) {
        local(() -> super.visitLambda(tree));
    }

    private void local(Runnable visit) {
        localDepth++;
        visit.run();
        localDepth--;
    }

    @Override
    public void visitVarDef(JCVariableDecl tree) {
        super.visitVarDef(tree);
        if (TreeUtil.hasAnnotation(tree.mods.annotations, Annotations.CONST_EVAL_SIMPLE)) {
            validate(tree).ifPresentOrElse(problem -> diagnostics.error(tree, "@ConstEval " + tree.name + ": " + problem),
                    () -> requests.add(new Request(enclosing.peekLast().binaryName().orElseThrow(), tree.name.toString(), tree,
                            "@ConstEval " + tree.name, literal -> tree.init = literal)));
        }
    }

    private Optional<String> validate(JCVariableDecl field) {
        Enclosing owner = enclosing.peekLast(); // a field always sits inside some class
        boolean staticFinal = owner.isInterface() || (field.mods.flags & (Flags.STATIC | Flags.FINAL)) == (Flags.STATIC | Flags.FINAL);
        if (owner.binaryName().isEmpty()) {
            return Optional.of("must be a field of a named class, not of a local or anonymous one");
        }
        if (!staticFinal) {
            return Optional.of("the field must be static final");
        }
        return field.init == null ? Optional.of("the field needs an initializer") : Optional.empty();
    }

    /** {@code Const.eval(expr)}: the expression becomes a hidden static field of the class; a placeholder waits for the literal. */
    @Override
    public void visitApply(JCMethodInvocation tree) {
        super.visitApply(tree);
        result = tree;
        if (!isConstEval(tree.meth) || tree.args.size() != 1) {
            return;
        }
        Enclosing owner = enclosing.peekLast(); // a call always sits inside some class
        Optional<String> binaryName = owner.binaryName();
        if (binaryName.isEmpty() || owner.isEnum()) {
            diagnostics.error(tree, "Const.eval: must be used inside a named class or interface, not a local or anonymous class or an enum");
            return;
        }
        String field = "$consteval$" + splices.size();
        splices.add(new Splice(owner.decl().pos, " static final Object " + field + " = " + tree.args.head + ";"));
        make.at(tree.pos);
        JCParens placeholder = make.Parens(tree);
        requests.add(new Request(binaryName.get(), field, tree, "Const.eval", literal -> placeholder.expr = literal));
        result = placeholder;
    }

    private static boolean isConstEval(JCExpression method) {
        return method instanceof JCFieldAccess access && access.name.contentEquals("eval")
                && TreeUtil.flatten(access.selected).filter(owner -> owner.equals("Const") || owner.equals("dev.specialize.Const")).isPresent();
    }


    /** Runs the side compilation (once, with the hidden fields spliced in) and replaces every request by its literal. */
    public void resolve() {
        if (requests.isEmpty()) {
            return;
        }
        Optional<String> source = patchedSource();
        for (Request request : requests) {
            EvaluationResult result = source.map(text -> evaluator.evaluate(unit, text, request.binaryName(), request.field()))
                    .orElseGet(() -> new Failure("cannot read the source file"));
            switch (result) {
                case Failure failure -> diagnostics.error(request.at(), request.label() + ": " + failure.message());
                case Value value -> Literals.of(make.at(request.at().pos), names, value.value()).ifPresentOrElse(
                        request.place(),
                        () -> diagnostics.error(request.at(), request.label() + ": a value of type " + value.value().getClass().getName()
                                + " cannot be written as a literal (primitives, String, enums, null and arrays of those can)"));
            }
        }
    }

    /** The original text with each hidden field inserted after its class's opening brace, last insertion first. */
    private Optional<String> patchedSource() {
        try {
            StringBuilder text = new StringBuilder(unit.sourcefile.getCharContent(true));
            splices.stream().sorted(Comparator.comparingInt(Splice::classPos).reversed())
                    .forEach(splice -> text.insert(text.indexOf("{", splice.classPos()) + 1, splice.declaration()));
            return Optional.of(text.toString());
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
