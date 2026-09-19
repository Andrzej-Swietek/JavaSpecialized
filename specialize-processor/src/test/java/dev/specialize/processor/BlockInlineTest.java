package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Multi-statement {@code @Inline} bodies become switch-expression blocks (or plain blocks for void). */
class BlockInlineTest {
    private static final String M = """
            package t;
            import dev.specialize.Inline;
            public class M {
                public static int log = 0;
                @Inline public static int normal(int x) { int y = x + 1; return y * 2; }
                @Inline public static long clampLong(long v, long lo, long hi) { if (v < lo) { return lo; } long r = v; if (r > hi) { r = hi; } return r; }
                @Inline public static void record(int x) { int doubled = x * 2; log = log * 100 + doubled; }
                @Inline public static void note(int x) { log = log * 100 + x; }
                @Inline public static <T> T id(T t) { return t; }
            }
            """;

    @Test
    void blocksAreInlinedWithRenamedLocalsAndEarlyReturns() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.M", M, "t.Use", """
                package t;
                public class Use {
                    public static String run(int k) {
                        int y = 10;                       // would clash with the body's local without renaming
                        int a = M.normal(k);
                        long b = M.clampLong(k * 100L, 0L, 250L) + M.clampLong(-5L, 0L, 250L);
                        M.record(k); M.note(7); M.record(next()); M.normal(k); M.<Integer>id(k);
                        return a + ":" + b + ":" + y + ":" + M.log;
                    }
                    static int counter;
                    static int next() { return ++counter; }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("8:250:10:60702", c.load("t.Use").getMethod("run", int.class).invoke(null, 3));
        String javap = javap(c, "t.Use");
        assertEquals(1, javap.split("Method t/M.normal", -1).length - 1, "as a statement a non-void block call is kept: " + javap);
        assertFalse(javap.contains("Method t/M.clampLong"), javap);
        assertFalse(javap.contains("Method t/M.note"), javap);
        assertEquals(1, javap.split("Method t/M.record", -1).length - 1, "record(next()) has an impure argument and stays a call: " + javap);
    }

    @Test
    void blockBodiesTravelThroughClassFilesAndVoidBlocksMayNotReturn() throws Exception {
        CompileHarness lib = CompileHarness.compile(Map.of("t.M", M));
        assertTrue(lib.success, lib.allDiagnostics());
        CompileHarness client = CompileHarness.compile(Map.of("c.C", """
                package c;
                public class C { public static int run() { t.M.record(2); return t.M.normal(4) + t.M.log; } }
                """), List.of(lib.classes), List.of());
        assertTrue(client.success, client.allDiagnostics());
        assertEquals(10 + 4, client.load("c.C", lib.classes).getMethod("run").invoke(null));
        assertFalse(javap(client, "c.C").contains("Method t/M.normal"));

        CompileHarness bad = CompileHarness.compile(Map.of("t.Bad", """
                package t;
                import dev.specialize.Inline;
                public class Bad {
                    @Inline public static void early(int x) { if (x > 0) { return; } x++; }
                    @Inline public static int empty() { }
                    @Inline public static int branches(int x) { if (x > 0) { return 1; } else { return 2; } }
                }
                """));
        assertFalse(bad.success);
        assertTrue(bad.errors().contains("t.Bad.early: a void block body must not contain 'return'"), bad.errors());
        assertTrue(bad.errors().contains("t.Bad.empty: the body must have at least one statement"), bad.errors());
        assertTrue(bad.errors().contains("t.Bad.branches: a block body must end with 'return <expression>;'"), bad.errors());
    }

    private static String javap(CompileHarness c, String cls) {
        java.io.StringWriter out = new java.io.StringWriter();
        java.util.spi.ToolProvider.findFirst("javap").orElseThrow().run(new java.io.PrintWriter(out), new java.io.PrintWriter(out),
                "-c", "-p", c.classes.resolve(cls.replace('.', '/') + ".class").toString());
        return out.toString();
    }
}
