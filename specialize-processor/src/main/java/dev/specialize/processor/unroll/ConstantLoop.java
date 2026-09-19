package dev.specialize.processor.unroll;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAssignOp;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Name;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

/** {@code for (int i = from; i <comparison> bound; i += step)} with literal {@code from}, {@code bound} and {@code step}. */
public record ConstantLoop(Name variable, int from, Comparison comparison, int bound, int step) {
    private static final Map<JCTree.Tag, Integer> INCREMENTS = Map.of(
            JCTree.Tag.PREINC, 1, JCTree.Tag.POSTINC, 1, JCTree.Tag.PREDEC, -1, JCTree.Tag.POSTDEC, -1);

    /** The loop conditions that bound a counted loop. */
    public enum Comparison {
        LT(JCTree.Tag.LT, (i, bound) -> i < bound), LE(JCTree.Tag.LE, (i, bound) -> i <= bound), GT(JCTree.Tag.GT, (i, bound) -> i > bound),
        GE(JCTree.Tag.GE, (i, bound) -> i >= bound), NE(JCTree.Tag.NE, (i, bound) -> i != bound);

        private final JCTree.Tag tag;
        private final LongPredicate2 holds;

        Comparison(JCTree.Tag tag, LongPredicate2 holds) {
            this.tag = tag;
            this.holds = holds;
        }

        static Optional<Comparison> of(JCTree.Tag tag) {
            return Arrays.stream(values()).filter(c -> c.tag == tag).findFirst();
        }

        boolean holds(long i, int bound) {
            return holds.test(i, bound);
        }

        private interface LongPredicate2 {
            boolean test(long i, int bound);
        }
    }

    public static Optional<ConstantLoop> of(JCForLoop loop) {
        return variable(loop).flatMap(decl -> bound(loop, decl.name).flatMap(bound -> step(loop, decl.name).flatMap(step ->
                intLiteral(decl.init).map(from -> new ConstantLoop(decl.name, from, Comparison.of(loop.cond.getTag()).orElseThrow(), bound, step)))));
    }

    /** The values the variable takes, or empty when there are more than {@code max} of them (an endless loop included). */
    public Optional<List<Integer>> iterations(int max) {
        List<Integer> values = LongStream.iterate(from, i -> comparison.holds(i, bound), i -> i + step) // long: no wrap at Integer.MAX_VALUE
                .limit(max + 1L).mapToObj(i -> (int) i).toList();
        return values.size() > max ? Optional.empty() : Optional.of(values);
    }

    private static Optional<JCVariableDecl> variable(JCForLoop loop) {
        return loop.init.size() == 1 && loop.init.head instanceof JCVariableDecl decl && decl.init != null
                && decl.vartype instanceof JCPrimitiveTypeTree type && type.typetag == TypeTag.INT
                ? Optional.of(decl) : Optional.empty();
    }

    private static Optional<Integer> bound(JCForLoop loop, Name variable) {
        return loop.cond instanceof JCBinary cond && Comparison.of(cond.getTag()).isPresent() && isVariable(cond.lhs, variable)
                ? intLiteral(cond.rhs) : Optional.empty();
    }

    private static Optional<Integer> step(JCForLoop loop, Name variable) {
        if (loop.step.size() != 1) {
            return Optional.empty();
        }
        return switch (loop.step.head.expr) {
            case JCUnary u when isVariable(u.arg, variable) -> Optional.ofNullable(INCREMENTS.get(u.getTag()));
            case JCAssignOp a when isVariable(a.lhs, variable) && a.getTag() == JCTree.Tag.PLUS_ASG -> intLiteral(a.rhs);
            case JCAssignOp a when isVariable(a.lhs, variable) && a.getTag() == JCTree.Tag.MINUS_ASG -> intLiteral(a.rhs).map(s -> -s);
            default -> Optional.empty();
        };
    }

    static boolean isVariable(JCExpression expression, Name variable) {
        return expression instanceof JCIdent id && id.name == variable;
    }

    /** {@code 5}, {@code -5}, {@code (5)}. */
    public static Optional<Integer> intLiteral(JCExpression expression) {
        return switch (expression) {
            case JCLiteral literal when literal.typetag == TypeTag.INT -> Optional.of((Integer) literal.value);
            case JCUnary negation when negation.getTag() == JCTree.Tag.NEG -> intLiteral(negation.arg).map(v -> -v);
            case JCParens parens -> intLiteral(parens.expr);
            default -> Optional.empty();
        };
    }
}
