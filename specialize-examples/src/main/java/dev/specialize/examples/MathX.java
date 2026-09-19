package dev.specialize.examples;

import dev.specialize.Inline;
import dev.specialize.TailRec;

/**
 * Compile-time inlined helpers. Each call site is replaced by the body; javac then constant-folds whatever is
 * constant: {@code MathX.sq(3)} becomes the literal {@code 9} in bytecode, {@code MathX.kb(4)} becomes {@code 4096L}.
 */
public final class MathX {
    public static final int KB_SHIFT = 10;

    private MathX() {
    }

    @Inline
    public static int sq(int x) {
        return x * x;
    }

    @Inline
    public static long kb(long n) {
        return n << KB_SHIFT;
    }

    @Inline
    public static double half(double x) {
        return x / 2;
    }

    @Inline
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    @Inline
    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Uses another inline method: nested expansion. */
    @Inline
    public static int sumOfSquares(int a, int b) {
        return sq(a) + sq(b);
    }

    @Inline
    public static boolean between(int v, int lo, int hi) {
        return v >= lo && v <= hi;
    }

    @Inline
    public static <T> T orDefault(T value, T fallback) {
        return value != null ? value : fallback;
    }

    @Inline
    public static void log(String message) {
        System.out.println("[MathX] " + message);
    }

    /** A block body: inlined as a switch-expression block, its local renamed so it cannot clash with the caller's. */
    @Inline
    public static int normal(int x) {
        int y = x + 1;
        return y * 2;
    }

    /** Scala-style {@code @tailrec}: compiled into a loop, so no stack depth is consumed. */
    @TailRec
    public static long gcd(long a, long b) {
        if (b == 0) {
            return a;
        }
        return gcd(b, a % b);
    }

    @TailRec
    public static long sumTo(long n, long acc) {
        return n == 0 ? acc : sumTo(n - 1, acc + n);
    }

    /** Statements only, no result: inlined as a plain block. */
    @Inline
    public static void trace(String what, int value) {
        String line = "[MathX] " + what + "=" + value;
        System.out.println(line);
    }
}
