package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InlineProcessingTest {

    @Test
    void overloadsNativeBodiesAnonymousClassesAndRecursion() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.M", """
                package t;
                import dev.specialize.Inline;
                public class M {
                    public static int helper(int x) { return x; }
                    public static int helper(long x) { return (int) x; }
                    @Inline public static int f(int x) { return x; }
                    @Inline public static int f(long x) { return (int) x; }
                    @Inline public static int viaHelper(int x) { return helper(x); }
                    @Inline public static Object obj() { return new Object(); }
                    @Inline public static int rec(int x) { return rec(x); }
                    @Inline public static <T> T id(T t) { return t; }
                    @Inline public static int g(int a) { return a; }
                    @Inline public static int g(int a, int b) { return a + b; }
                    @Inline public static int nested(Holder h) { return h.v; }
                    public static class Holder { public int v = 4; }
                    public static void run() { }
                }
                """, "t.Use", """
                package t;
                public class Use {
                    public static int run() { return M.f(1) + M.viaHelper(2) + (M.obj() == null ? 0 : 1) + M.<Integer>id(3) + M.g(0) + M.nested(new M.Holder()) - 4; }
                    public static int deep() { return M.rec(0); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals(1 + 2 + 1 + 3, c.load("t.Use").getMethod("run").invoke(null));
        String javap = javap(c, "t.Use");
        assertTrue(javap.contains("M.f:(I)I"), "type-only overloads are not inlined: " + javap);
        assertTrue(javap.contains("M.helper:(I)I"), "static members are qualified: " + javap);
        assertTrue(javap.contains("M.rec:(I)I"), "recursion stops at the depth limit: " + javap);
        assertTrue(javap.contains("M.id:("), "explicit type arguments are not inlined: " + javap);
    }

    @Test
    void rejectsNativeAndAnonymousClassBodies() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.M", """
                package t;
                import dev.specialize.Inline;
                public class M {
                    @Inline public static native int nat(int x);
                    @Inline public static Runnable anon() { return new Runnable() { public void run() { } }; }
                }
                """));
        assertFalse(c.success);
        assertTrue(c.errors().contains("t.M.nat: the body must have at least one statement"), c.errors());
        assertTrue(c.errors().contains("t.M.anon: bodies containing lambdas or anonymous classes"), c.errors());
    }

    @Test
    void classFileBodiesAreParsedAndBrokenOnesReported() throws Exception {
        CompileHarness lib = CompileHarness.compileUnprocessed(Map.of("lib.L", """
                package lib;
                import dev.specialize.InlineBody;
                public class L {
                    @InlineBody(params = {"x"}, paramTypes = {"int"}, returnType = "int", body = "x +")
                    public static int broken(int x) { return x; }
                    @InlineBody(params = {"x"}, paramTypes = {"int"}, returnType = "int", body = "x) y")
                    public static int trailing(int x) { return x; }
                    @InlineBody(params = {"x"}, paramTypes = {"int("}, returnType = "int", body = "x")
                    public static int badType(int x) { return x; }
                    @InlineBody(params = {"x"}, paramTypes = {}, returnType = "int", body = "x")
                    public static int lengths(int x) { return x; }
                    @InlineBody(params = {"x", "y"}, paramTypes = {"", "java.util.List<java.lang.String>"}, body = "y.get(x)")
                    public static <T> String pick(T x, java.util.List<String> y) { return y.get(0); }
                    public static int plain(int x) { return x; }
                }
                """), List.of());
        assertTrue(lib.success, lib.allDiagnostics());
        CompileHarness c = CompileHarness.compile(Map.of("t.Use", """
                package t;
                import static lib.L.*;
                import static nope.Missing.gone;
                public class Use {
                    public static int run() { return broken(1) + plain(2) + pick(0, java.util.List.of("a")).length() + gone() + trailing(1) + badType(1) + lengths(1); }
                }
                """), List.of(lib.classes), List.of());
        assertFalse(c.success, "the bogus static import fails attribution: " + c.allDiagnostics());
        assertTrue(c.allDiagnostics().contains("cannot parse @InlineBody of lib.L.broken"), c.allDiagnostics());
        assertTrue(c.allDiagnostics().contains("lib.L.trailing: not a valid Java expression: x) y"), c.allDiagnostics());
        assertTrue(c.allDiagnostics().contains("lib.L.badType: not a valid Java type: int("), c.allDiagnostics());
        assertTrue(c.allDiagnostics().contains("lib.L.lengths: params and paramTypes differ in length"), c.allDiagnostics());
    }

    private static String javap(CompileHarness c, String cls) {
        java.io.StringWriter out = new java.io.StringWriter();
        java.util.spi.ToolProvider.findFirst("javap").orElseThrow().run(new java.io.PrintWriter(out), new java.io.PrintWriter(out),
                "-c", "-p", c.classes.resolve(cls.replace('.', '/') + ".class").toString());
        return out.toString();
    }
}
