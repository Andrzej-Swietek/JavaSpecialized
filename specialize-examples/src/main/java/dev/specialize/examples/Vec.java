package dev.specialize.examples;

import dev.specialize.Inline;
import dev.specialize.Prim;
import dev.specialize.Specialize;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

/** Generic array algorithms with primitive overloads generated next to them: {@code Vec.sum(int[], …)} boxes nothing. */
public final class Vec {
    private Vec() {
    }

    @Specialize(types = {int.class, long.class, double.class})
    public static <T> T fold(T[] xs, T zero, BinaryOperator<T> plus) {
        T acc = zero;
        for (T x : xs) {
            acc = plus.apply(acc, x);
        }
        return acc;
    }

    @Specialize(types = {int.class, double.class})
    public static <T> int indexOf(T[] xs, T value) {
        for (int i = 0; i < xs.length; i++) {
            if (Prim.eq(xs[i], value)) {
                return i;
            }
        }
        return -1;
    }

    /** The lambda is applied where {@code f} is called: {@code Vec.sumOf(xs, x -> x * x)} compiles to one loop, no functional object. */
    @Inline
    public static int sumOf(int[] xs, IntUnaryOperator f) {
        int s = 0;
        for (int i = 0; i < xs.length; i++) {
            s += f.applyAsInt(xs[i]);
        }
        return s;
    }

    @Inline
    public static void each(int[] xs, IntConsumer action) {
        for (int x : xs) {
            action.accept(x);
        }
    }

    /** No parameter is a bare {@code T} or {@code T[]}: the overload would clash by erasure, so it is named {@code describeInt}. */
    @Specialize(types = int.class)
    public static <T> String describe(Function<T, String> label, List<T> xs) {
        StringBuilder sb = new StringBuilder();
        for (T x : xs) {
            sb.append(label.apply(x)).append(' ');
        }
        return sb.toString().trim();
    }
}
