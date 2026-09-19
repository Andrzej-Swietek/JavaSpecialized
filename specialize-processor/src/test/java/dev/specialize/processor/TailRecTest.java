package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TailRecTest {

    @Test
    void tailCallsBecomeLoopsInEveryTailPosition() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.R", """
                package t;
                import dev.specialize.TailRec;
                public final class R {
                    @TailRec public static long gcd(long a, long b) { if (b == 0) { return a; } return gcd(b, a % b); }
                    @TailRec public static long fact(int n, long acc) { return n <= 1 ? acc : fact(n - 1, acc * n); }
                    @TailRec private static int count(int n, int acc) {
                        switch (n % 2) { case 0: return R.count(n - 1, acc + 1); case 5: seen++; default: { if (n <= 0) return acc; else return count(n - 1, acc + 1); } }
                    }
                    @TailRec public static <T> T last(java.util.List<T> xs, int i) { return i == xs.size() - 1 ? xs.get(i) : last(xs, i + 1); }
                    public static int seen;
                    @TailRec public static void countdown(int n) { if (n == 0) { return; } seen++; countdown(n - 1); }
                    @TailRec public static void ping(int n) { if (n > 0) { seen += n; ping(n - 1); } }
                    @TailRec public final int swap(int a, int b, int k) { if (k == 0) return a * 10 + b; return this.swap(b, a, k - 1); }
                    private static int count(int n) { return n; }
                    @TailRec public int inFinalClass(int n) { return (n == 0 ? 0 : (inFinalClass(n - 1))); }
                    @TailRec public static int arrows(int n, int acc) { switch (n) { case 0 -> { return acc; } default -> { return arrows(n - 1, acc + 1); } } }
                    @TailRec public static int labeled(int n) { out: { if (n == 0) { return 7; } else { return labeled(n - 1); } } }
                    @TailRec public static void voidReturn(int n) { if (n <= 0) return; else voidReturn(n - 1); }
                    @TailRec public static void voidOther(int n) { if (n <= 0) count(n); else voidOther(n - 1); }
                    @TailRec public static void voidEmpty(int n) { if (n <= 0) { } else voidEmpty(n - 1); }
                    @TailRec public static void endsWithTry(int n) { if (n > 0) { endsWithTry(n - 1); } else { try { seen++; } finally { seen--; } } }
                    @TailRec public static void endsWithSync(int n) { if (n > 0) { endsWithSync(n - 1); } else { synchronized (R.class) { seen++; seen--; } } }
                    @TailRec public static void endsWithAssign(int n) { if (n > 0) endsWithAssign(n - 1); else seen += 0; }
                    @TailRec public static void endsWithIfElse(int n) { if (n > 0) endsWithIfElse(n - 1); else if (n == 0) { return; } else { throw new IllegalStateException(); } }
                    public static String run() {
                        R r = new R();
                        return gcd(1071, 462) + ":" + fact(20, 1) + ":" + count(1_000_000, 0) + ":" + last(java.util.List.of("a", "b", "c"), 0)
                                + ":" + r.swap(1, 2, 3) + ":" + r.swap(1, 2, 4);
                    }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        Class<?> r = c.load("t.R");
        assertEquals("21:2432902008176640000:1000001:c:21:12", r.getMethod("run").invoke(null));
        r.getMethod("countdown", int.class).invoke(null, 2_000_000); // would overflow the stack as real recursion
        assertEquals(2_000_000, r.getField("seen").get(null));
        r.getMethod("ping", int.class).invoke(null, 3);
        assertEquals(2_000_006, r.getField("seen").get(null));
        assertEquals(0, r.getMethod("inFinalClass", int.class).invoke(r.getConstructor().newInstance(), 5));
        assertEquals(7, r.getMethod("arrows", int.class, int.class).invoke(null, 7, 0));
        assertEquals(7, r.getMethod("labeled", int.class).invoke(null, 3));
        for (String v : java.util.List.of("voidReturn", "voidOther", "voidEmpty", "endsWithTry", "endsWithSync", "endsWithAssign", "endsWithIfElse")) {
            r.getMethod(v, int.class).invoke(null, 3);
        }
        assertEquals(2_000_006, r.getField("seen").get(null));
        String javap = javap(c, "t.R");
        for (String method : java.util.List.of("gcd", "fact", "count", "last", "countdown", "ping", "swap")) {
            int start = javap.indexOf(" " + method + "(");
            int end = javap.indexOf("\n\n", start);
            String section = javap.substring(start, end < 0 ? javap.length() : end);
            assertFalse(section.contains("Method " + method + ":"), "no recursive call left in " + method + ": " + section);
            assertTrue(section.contains("goto"), "a loop instead: " + section);
        }
    }

    @Test
    void refusesWhatScalaRefuses() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Bad", """
                package t;
                import dev.specialize.TailRec;
                public class Bad {
                    @TailRec public int overridable(int n) { return n == 0 ? 0 : overridable(n - 1); }
                    @TailRec public static int notTail(int n) { return n == 0 ? 0 : 1 + notTail(n - 1); }
                    @TailRec public static int inTry(int n) { try { return inTry(n - 1); } finally { n++; } }
                    @TailRec public static int notRecursive(int n) { return n; }
                    @TailRec public static int overloaded(int n) { return overloaded(n - 1); }
                    public static int overloaded(long n) { return 0; }
                    @TailRec public static int finalParam(final int n) { return finalParam(n); }
                    @TailRec public static int inLambda(int n) { java.util.function.IntUnaryOperator f = k -> inLambda(k); return f.applyAsInt(n); }
                    @TailRec public static native int noBody(int n);
                    @TailRec public static int qualified(int n) { return n == 0 ? 0 : t.Bad.qualified(n - 1); }
                    @TailRec public static int otherReceiver(Bad other, int n) { return n == 0 ? 0 : other.otherReceiver(other, n - 1); }
                }
                """));
        assertFalse(c.success);
        String errors = c.errors();
        assertTrue(errors.contains("@TailRec overridable: the method must be static, private or final"), errors);
        assertTrue(errors.contains("@TailRec notTail: recursive call not in tail position"), errors);
        assertTrue(errors.contains("@TailRec inTry: recursive call not in tail position"), errors);
        assertTrue(errors.contains("@TailRec notRecursive: no tail call to itself found"), errors);
        assertTrue(errors.contains("@TailRec overloaded: an overload with the same number of parameters"), errors);
        assertTrue(errors.contains("@TailRec finalParam: parameters must not be final"), errors);
        assertTrue(errors.contains("@TailRec inLambda: recursive call not in tail position"), errors);
        assertTrue(errors.contains("@TailRec noBody: the method needs a body"), errors);
        assertTrue(errors.contains("@TailRec qualified: no tail call to itself found"), "only f(..), this.f(..) and Bad.f(..) count: " + errors);
        assertTrue(errors.contains("@TailRec otherReceiver: no tail call to itself found"), errors);
        assertTrue(c.allDiagnostics().contains("Bad.java:5:"), "reported at the offending call: " + c.allDiagnostics());
    }

    static String javap(CompileHarness c, String cls) {
        return javap(c, cls, "-c");
    }

    static String javap(CompileHarness c, String cls, String detail) {
        java.io.StringWriter out = new java.io.StringWriter();
        java.util.spi.ToolProvider.findFirst("javap").orElseThrow().run(new java.io.PrintWriter(out), new java.io.PrintWriter(out),
                detail, "-p", c.classes.resolve(cls.replace('.', '/') + ".class").toString());
        return out.toString();
    }
}
