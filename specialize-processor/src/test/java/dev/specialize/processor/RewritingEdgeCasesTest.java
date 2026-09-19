package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RewritingEdgeCasesTest {
    private static final String BOX = """
            package t;
            import dev.specialize.Specialize;
            @Specialize(types = {int.class, long.class})
            public class Box<T> {
                public T v;
                public static <T> Box<T> of(T v) { Box<T> b = new Box<>(); b.v = v; return b; }
                public static <T> Box<T> none() { return new Box<>(); }
                public Box<T> self() { return this; }
                public <T> T shadow(T x) { return x; }
                public <T> Box<T> shadowOf(T x) { return Box.of(x); }
                public static class Nested<T> { public T inner; public T same(T x) { return x; } }
                public static class Plain { public int n = 1; }
                public static class Other<U> { public U u; }
            }
            """;

    @Test
    void staticImportsSwitchExpressionsAndReceiverChainsAreRetargeted() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX, "t.Use", """
                package t;
                import static t.Box.none;
                import static t.Box.*;
                public class Use {
                    public static Box<int> viaStaticImport() { return none(); }
                    public static Box<int> viaOnDemandImport() { Box<int> b = of(1); b = none(); return b; }
                    public static Box<int> viaSwitch(int k) { return switch (k) { case 1 -> Box.of(1); default -> Box.none(); }; }
                    public static Box<int> viaSwitchYield(int k) {
                        return switch (k) {
                            case 1 -> { Runnable r = () -> { }; yield Box.none(); }
                            case 2 -> { int inner = switch (k) { default -> 0; }; yield Box.of(inner); }
                            case 3 -> { class Local { } yield Box.of(new Local() == null ? 3 : 4); }
                            default -> Box.of(2);
                        };
                    }
                    public static Box<int> chain() { return Box.none().self().self(); }
                    public static Box<int> ternaryChain(boolean b) { return b ? Box.none().self() : (Box.none()); }
                    public static Box<int> parensChain() { return (Box.none()).self(); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        Class<?> use = c.load("t.Use");
        for (String m : java.util.List.of("viaStaticImport", "viaOnDemandImport", "chain", "parensChain")) {
            assertEquals("t.BoxInt", use.getDeclaredMethod(m).invoke(null).getClass().getName(), m);
        }
        assertEquals("t.BoxInt", use.getDeclaredMethod("viaSwitch", int.class).invoke(null, 0).getClass().getName());
        assertEquals("t.BoxInt", use.getDeclaredMethod("viaSwitchYield", int.class).invoke(null, 1).getClass().getName());
        assertEquals("t.BoxInt", use.getDeclaredMethod("ternaryChain", boolean.class).invoke(null, true).getClass().getName());
    }

    @Test
    void recordsAndInterfacesAreTemplatesToo() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Pair", """
                package t;
                import dev.specialize.Prim;
                import dev.specialize.Specialize;
                @Specialize(types = int.class)
                public record Pair<T>(T a, T b) implements java.io.Serializable {
                    static int created = 0;
                    public Pair { if (Prim.isNull(a)) { throw new IllegalArgumentException("a"); } created++; }
                    public static <T> Pair<T> of(T a, T b) { return new Pair<>(a, b); }
                    public T first() { return a; }
                }
                """, "t.Single", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = int.class)
                public record Single<T>(T v) { }
                """, "t.Fn", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = int.class)
                public interface Fn<T> {
                    T apply(T x);
                    static <T> Fn<T> identity() { return x -> x; }
                    default Fn<T> twice() { return x -> apply(apply(x)); }
                }
                """, "t.Use", """
                package t;
                public class Use {
                    public static int run() { Pair<int> p = Pair.of(1, 2); Fn<int> f = Fn.identity(); return p.first() + f.twice().apply(3); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics() + (c.generated("t.PairInt") ? c.generatedSource("t.PairInt") : ""));
        assertEquals(4, c.load("t.Use").getDeclaredMethod("run").invoke(null));
        assertTrue(c.load("t.PairInt").isRecord());
        assertEquals(int.class, c.load("t.SingleInt").getRecordComponents()[0].getType());
        assertEquals(2, c.load("t.PairInt").getRecordComponents().length);
        assertEquals(int.class, c.load("t.PairInt").getRecordComponents()[0].getType());
        String pairInt = c.generatedSource("t.PairInt");
        assertTrue(pairInt.contains("public record PairInt(int a, int b) implements java.io.Serializable {"), pairInt);
        assertTrue(java.io.Serializable.class.isAssignableFrom(c.load("t.PairInt")), pairInt);
        assertTrue(pairInt.contains("public PairInt {"), pairInt);
        assertTrue(c.load("t.FnInt").isInterface());
        assertEquals(int.class, c.load("t.FnInt").getMethod("apply", int.class).getReturnType());
    }

    @Test
    void shadowingTypeVariablesAreLeftAlone() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX, "t.Use", """
                package t;
                public class Use {
                    public static Object run() { Box<int> b = Box.of(1); Box.Nested<String> n = new Box.Nested<>(); n.inner = "n"; return b.shadow("str") + n.same(n.inner); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("strn", c.load("t.Use").getDeclaredMethod("run").invoke(null));
        String boxInt = c.generatedSource("t.BoxInt");
        assertTrue(boxInt.contains("public <T>T shadow(T x)"), boxInt);
        assertTrue(boxInt.contains("return Box.of(x);"), "a call on the template inside a shadowing method stays generic: " + boxInt);
        assertTrue(boxInt.contains("public T same(T x)"), boxInt);
    }

    @Test
    void inliningNeverReordersOrDropsSideEffects() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.M", """
                package t;
                import dev.specialize.Inline;
                public class M {
                    static int calls = 0;
                    static int a() { calls = calls * 10 + 1; return 1; }
                    static int b() { calls = calls * 10 + 2; return 2; }
                    @Inline static int swapped(int x, int y) { return y - x; }
                    @Inline static int unused(int x) { return 0; }
                    @Inline static int cond(boolean c, int x, int y) { return c ? x : y; }
                    @Inline static int sq(int x) { return x * x; }
                    public static String run(int[] arr, int k) {
                        int s = swapped(a(), b()); int u = unused(a()); int w = cond(true, a(), b());
                        return calls + ":" + s + u + w + ":" + sq(k + 2) + sq(k > 0 ? k : -k) + (sq(arr[0]) == 4 ? "" : "?");
                    }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("12112:101:164", c.load("t.M").getMethod("run", int[].class, int.class).invoke(null, new int[]{2}, 2));
        String javap = javap(c, "t.M");
        assertTrue(javap.contains("Method swapped:(II)I") && javap.contains("Method unused:(I)I") && javap.contains("Method cond:(ZII)I"),
                "calls with side-effecting arguments are kept: " + javap);
        assertEquals(1, javap.split("Method sq:\\(I\\)I", -1).length - 1, "pure arguments are inlined, the array read is kept: " + javap);
    }

    private static String javap(CompileHarness c, String cls) {
        java.io.StringWriter out = new java.io.StringWriter();
        java.util.spi.ToolProvider.findFirst("javap").orElseThrow().run(new java.io.PrintWriter(out), new java.io.PrintWriter(out),
                "-c", "-p", c.classes.resolve(cls.replace('.', '/') + ".class").toString());
        return out.toString();
    }
}
