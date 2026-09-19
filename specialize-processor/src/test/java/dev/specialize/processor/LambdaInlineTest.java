package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Lambdas handed to {@code @Inline} methods are applied in place: no functional object, no call. */
class LambdaInlineTest {
    private static final String LIB = """
            package t;
            import dev.specialize.Inline;
            import java.util.function.IntBinaryOperator;
            import java.util.function.IntConsumer;
            import java.util.function.IntPredicate;
            import java.util.function.IntUnaryOperator;
            import java.util.function.Supplier;
            public final class Lib {
                @Inline public static int sumOf(int[] xs, IntUnaryOperator f) { int s = 0; for (int i = 0; i < xs.length; i++) { s += f.applyAsInt(xs[i]); } return s; }
                @Inline public static int fold(int[] xs, int zero, IntBinaryOperator op) { int acc = zero; for (int x : xs) { acc = op.applyAsInt(acc, x); } return acc; }
                @Inline public static void each(int[] xs, IntConsumer c) { for (int x : xs) { c.accept(x); } }
                @Inline public static int count(int[] xs, IntPredicate p) { int n = 0; for (int x : xs) { if (p.test(x)) { n++; } } return n; }
                @Inline public static int twice(int x, IntUnaryOperator f) { return f.applyAsInt(f.applyAsInt(x)); }
                @Inline public static int firstOr(int[] xs, Supplier<Integer> fallback) { return xs.length > 0 ? xs[0] : fallback.get(); }
                @Inline public static <T> T pass(T x, java.util.function.UnaryOperator<T> f) { return f.apply(x); }
                @Inline public static void run(int x, IntConsumer c) { c.accept(x); c.accept(x + 1); }
                @Inline public static int applyTwice(IntUnaryOperator f, int x) { IntUnaryOperator g = f; return g.applyAsInt(x) + f.applyAsInt(x); }
                @Inline public static void discard(int x, IntUnaryOperator f) { f.applyAsInt(x); }
                @Inline public static int atZero(IntUnaryOperator f) { return f.applyAsInt(0); }
                @Inline public static void viaLocal(int x, IntConsumer c) { IntConsumer g = c; g.accept(x); }
                public static int sum;
                @Inline public static void ping(int x) { sum += x; }
                @Inline public static int plusOne(int x) { return x + 1; }
                @Inline public static int twicePlus(int x, IntUnaryOperator f) { return plusOne(f.applyAsInt(x)); }
                @Inline public static void touch(int x, IntConsumer c) { Integer.valueOf(x).toString(); c.accept(x); }
            }
            """;

    @Test
    void lambdaBodiesAreAppliedWhereTheInlineBodyCallsThem() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Lib", LIB, "t.Use", """
                package t;
                import static t.Lib.*;
                public class Use {
                    static int sum;
                    static int[] data = {1, 2, 3};
                    public static String run() {
                        int a = sumOf(data, x -> x * 2);                                   // expression lambda, non-pure argument → temp
                        int b = fold(data, 0, (acc, x) -> acc + x);                        // two parameters
                        sum = 0;
                        each(data, x -> sum += x);                                         // statement context, assignment expression
                        int c = sum;
                        sum = 0;
                        each(data, x -> { int d = x * 10; sum += d; });                    // statement context, block lambda with a local
                        int e = count(data, x -> { boolean odd = x % 2 == 1; return odd; });   // block lambda with a result
                        int f = twice(5, x -> x + 1);                                      // pure argument → direct substitution, applied twice
                        int[] none = new int[0];
                        int g = firstOr(none, () -> 9);                                    // no parameters
                        int h = applyTwice(x -> x - 1, 10);                                // lambda also stored in a local: cast form
                        java.util.function.IntUnaryOperator inc = x -> x + 1;
                        int i = sumOf(data, inc);                                          // not a lambda: ordinary argument
                        int j = sumOf(data, x -> { java.util.function.IntSupplier s = () -> { return x; }; return s.getAsInt(); });   // nested lambda keeps its return
                        int k = atZero(x -> x + 7);                                         // literal argument
                        int l = count(data, x -> { Object o = new Object() { public String toString() { return "o"; } }; return o.toString().length() == x; });   // anonymous class keeps its return
                        java.util.function.IntConsumer noop = x -> { };
                        each(data, noop);                                                  // statement call on a non-lambda argument
                        viaLocal(3, x -> { });                                             // statement call on a local, not a parameter
                        Runnable r = () -> Lib.ping(2);                                    // void expression body in expression position
                        r.run();
                        int m = twicePlus(1, x -> x);                                       // an unqualified call inside the body
                        touch(1, x -> Lib.ping(x));                                        // a statement whose receiver is a call
                        return a + ":" + b + ":" + c + ":" + sum + ":" + e + ":" + f + ":" + g + ":" + h + ":" + i + ":" + j + ":" + k + ":" + l + ":" + Lib.sum + ":" + m;
                    }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("12:6:6:60:2:7:9:18:9:6:7:1:3:2", c.load("t.Use").getMethod("run").invoke(null));
        String run = TailRecTest.javap(c, "t.Use");
        String body = run.substring(run.indexOf("run()"), run.indexOf("static {}"));
        assertEquals(5, lambdaObjects(body), "only `inc`, `noop`, the local `g`, the nested supplier and `viaLocal`'s local create functional objects:\n" + body);
        assertEquals(1, body.lines().filter(l -> l.contains("invokedynamic") && l.contains("Runnable")).count(), "plus the Runnable `r`");
        assertFalse(body.contains("Method t/Lib."), "no call into Lib remains:\n" + body);
    }

    @Test
    void lambdasThatCannotBeAppliedStayLambdas() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Lib", LIB, "t.Use", """
                package t;
                import static t.Lib.*;
                public class Use {
                    static int sum;
                    public static String run() {
                        int[] data = {1, 2};
                        each(data, x -> { if (x > 1) { return; } sum += x; });            // `return;` in a void block: kept as a call
                        each(data, x -> { Runnable r = new Runnable() { public void run() { return; } }; r.run(); });   // a class's return is its own: applied
                        each(data, x -> { Runnable r2 = () -> { return; }; r2.run(); });   // a nested lambda's return too
                        run(1, x -> Math.abs(x));                                          // a call is a statement expression → applied
                        discard(2, x -> x * 2);                                            // `x * 2;` is not a statement → kept as a call
                        int k = count(data, x -> { if (x > 0) { return true; } else { return false; } });   // block not ending in return: kept
                        String p = pass("a", s -> s + "b");                                // generic parameter type: not inlined at all
                        return sum + ":" + k + ":" + p;
                    }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("1:2:ab", c.load("t.Use").getMethod("run").invoke(null));
        String run = TailRecTest.javap(c, "t.Use");
        assertTrue(run.contains("Method t/Lib.pass"), "generic inline with a lambda is kept:\n" + run);
        assertEquals(4, lambdaObjects(run.substring(run.indexOf("run()"))), run);
        assertEquals(1, run.substring(run.indexOf("run()")).lines().filter(l -> l.contains("invokedynamic") && l.contains("Runnable")).count(),
                "the anonymous Runnable is a class, only r2 is a lambda\n" + run);
    }

    private static long lambdaObjects(String javap) {
        return javap.lines().filter(l -> l.contains("invokedynamic") && l.contains("java/util/function")).count();
    }
}
