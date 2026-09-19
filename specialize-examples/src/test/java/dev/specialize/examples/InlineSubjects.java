package dev.specialize.examples;

import dev.specialize.Inline;

/** Code under test for {@link InlineTest}, kept free of assertion strings so javap output can be searched. */
final class InlineSubjects {
    static final int CONSTANT = MathX.sq(3) + MathX.sq(4);

    int counter;

    @Inline
    static int localTriple(int x) {
        return x * 3;
    }

    int square(int x) {
        return MathX.sq(x);
    }

    double halfOfInt(int x) {
        return MathX.half(x);
    }

    long kilobytes() {
        return MathX.kb(4);
    }

    double lerpHalf() {
        return MathX.lerp(0, 10, 0.5);
    }

    int clamp(int v) {
        return MathX.clamp(v, 0, 10);
    }

    int sumOfSquares(int a, int b) {
        return MathX.sumOfSquares(a, b);
    }

    int tripled(int x) {
        return localTriple(x);
    }

    int normal(int x) {
        int y = 100;                      // the body's local `y` is renamed, so this one is untouched
        return MathX.normal(x) + y;
    }

    void trace(int v) {
        MathX.trace("v", v);
    }

    int next() {
        return ++counter;
    }

    int noDoubleEvaluation() {
        return MathX.between(next(), 0, 10) ? 1 : 0;
    }

    static <T> T orDefault(T v, T d) {
        return MathX.orDefault(v, d);
    }
}
