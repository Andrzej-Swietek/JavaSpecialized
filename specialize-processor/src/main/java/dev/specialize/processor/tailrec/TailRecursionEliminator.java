package dev.specialize.processor.tailrec;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Names;
import dev.specialize.processor.Diagnostics;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.TreeUtil;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@code @TailRec}: rewrites a self-recursive method into a loop. {@code return f(a, b);} in tail position becomes
 * "assign the parameters through temporaries, {@code continue}"; the body runs inside {@code while (true)}.
 */
public final class TailRecursionEliminator extends TreeTranslator {
    private final TreeMaker make;
    private final Names names;
    private final Diagnostics diagnostics;
    private JCClassDecl enclosing;

    public TailRecursionEliminator(TreeMaker make, Names names, Diagnostics diagnostics) {
        this.make = make;
        this.names = names;
        this.diagnostics = diagnostics;
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        JCClassDecl saved = enclosing;
        enclosing = tree;
        super.visitClassDef(tree);
        enclosing = saved;
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        super.visitMethodDef(tree);
        if (TreeUtil.hasAnnotation(tree.mods.annotations, Annotations.TAIL_REC_SIMPLE)) {
            validate(tree).ifPresentOrElse(problem -> diagnostics.error(tree, "@TailRec " + tree.name + ": " + problem),
                    () -> new TailCallLoop(make, names, diagnostics, enclosing, tree).apply());
        }
        result = tree;
    }

    private Optional<String> validate(JCMethodDecl method) {
        boolean overridable = (method.mods.flags & (Flags.STATIC | Flags.PRIVATE | Flags.FINAL)) == 0 && (enclosing.mods.flags & Flags.FINAL) == 0;
        long sameArity = enclosing.defs.stream()
                .filter(def -> def instanceof JCMethodDecl m && m.name == method.name && m.params.size() == method.params.size())
                .count();
        return Stream.of(
                        check(overridable, "the method must be static, private or final (or its class final); an overridable method is left recursive: add the modifier or remove @TailRec"),
                        check(method.body == null, "the method needs a body"),
                        check(sameArity > 1, "an overload with the same number of parameters makes the recursive calls ambiguous"),
                        check(method.params.stream().anyMatch(p -> (p.mods.flags & Flags.FINAL) != 0), "parameters must not be final, they are reassigned by the loop"))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> check(boolean failed, String message) {
        return failed ? Optional.of(message) : Optional.empty();
    }
}
