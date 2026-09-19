package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.specialize.processor.consteval.ConstantEvaluator;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConstEvalTest {

    @Test
    void initializersAreReplacedByTheirValues() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Tables", """
                package t;
                import dev.specialize.ConstEval;
                import java.util.concurrent.TimeUnit;
                public class Tables {
                    public enum Kind { A, B }
                    @ConstEval public static final int[] CRC = crc(0xEDB88320, 8);
                    @ConstEval public static final long MASK = mask(40);
                    @ConstEval public static final String GREETING = "hel" + "lo".toUpperCase();
                    @ConstEval public static final double NAN = 0.0 / 0.0;
                    @ConstEval public static final double INF = 1.0 / 0.0;
                    @ConstEval public static final double NINF = -1.0 / 0.0;
                    @ConstEval public static final double TAU = Math.PI * 2;
                    @ConstEval public static final float PINF = 1f / 0f;
                    @ConstEval public static final float NEG = -1f / 0f;
                    @ConstEval public static final float NANF = 0f / 0f;
                    @ConstEval public static final float FL = 1.5f * 2;
                    @ConstEval public static final short SH = (short) (300 + 1);
                    @ConstEval public static final byte BY = (byte) (2 + 3);
                    @ConstEval public static final char CH = "xyz".charAt(1);
                    @ConstEval public static final boolean B = "abc".length() == 3;
                    @ConstEval public static final Object NOTHING = System.getProperty("does.not.exist.anywhere");
                    @ConstEval public static final Kind KIND = Kind.valueOf("B");
                    @ConstEval public static final TimeUnit UNIT = TimeUnit.valueOf("SECONDS");
                    @ConstEval public static final Integer BOXED = Integer.valueOf("42");
                    @ConstEval public static final String[][] GRID = {{"a", "b"}, {"c"}};
                    @ConstEval public static final Long[] BOXES = {1L, 2L};
                    @ConstEval static final int HIDDEN = Nested.VALUE * 2;
                    public static class Nested {
                        @ConstEval static final int VALUE = fib(30);
                        static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
                    }
                    public interface Constants { @ConstEval int SIZE = Integer.parseInt("12"); }
                    static int[] crc(int poly, int n) { int[] t = new int[n]; for (int i = 0; i < n; i++) { int c = i; for (int k = 0; k < 8; k++) { c = (c & 1) != 0 ? poly ^ (c >>> 1) : c >>> 1; } t[i] = c; } return t; }
                    static long mask(int bits) { return (1L << bits) - 1; }
                    public static int useMask() { return (int) (MASK >>> 32); }
                }
                """));
        assertTrue(c.success, "diagnostics: " + c.diagnostics);
        Class<?> tables = c.load("t.Tables");
        assertArrayEquals(new int[]{0, 1996959894, -301047508, -1727442502, 124634137, 1886057615, -379345611, -1637575261},
                (int[]) tables.getField("CRC").get(null));
        assertEquals((1L << 40) - 1, tables.getField("MASK").get(null));
        assertEquals("helLO", tables.getField("GREETING").get(null));
        assertTrue(Double.isNaN((Double) tables.getField("NAN").get(null)));
        assertEquals(Double.POSITIVE_INFINITY, tables.getField("INF").get(null));
        assertEquals(Double.NEGATIVE_INFINITY, tables.getField("NINF").get(null));
        assertEquals(Math.PI * 2, tables.getField("TAU").get(null));
        assertEquals(Float.POSITIVE_INFINITY, tables.getField("PINF").get(null));
        assertEquals(Float.NEGATIVE_INFINITY, tables.getField("NEG").get(null));
        assertTrue(Float.isNaN((Float) tables.getField("NANF").get(null)));
        assertEquals(3.0f, tables.getField("FL").get(null));
        assertEquals((short) 301, tables.getField("SH").get(null));
        assertEquals((byte) 5, tables.getField("BY").get(null));
        assertEquals('y', tables.getField("CH").get(null));
        assertEquals(true, tables.getField("B").get(null));
        assertEquals(null, tables.getField("NOTHING").get(null));
        assertEquals("B", tables.getField("KIND").get(null).toString());
        assertEquals(java.util.concurrent.TimeUnit.SECONDS, tables.getField("UNIT").get(null));
        assertEquals(42, tables.getField("BOXED").get(null));
        assertArrayEquals(new String[][]{{"a", "b"}, {"c"}}, (String[][]) tables.getField("GRID").get(null));
        assertArrayEquals(new Long[]{1L, 2L}, (Long[]) tables.getField("BOXES").get(null));
        assertEquals(832040 * 2, field(tables, "HIDDEN"));
        assertEquals(832040, field(c.load("t.Tables$Nested"), "VALUE"));
        assertEquals(12, c.load("t.Tables$Constants").getField("SIZE").get(null));
        String javap = TailRecTest.javap(c, "t.Tables", "-v");
        assertTrue(javap.contains("ConstantValue: long 1099511627775"), "MASK is a constant variable\n" + javap);
        assertTrue(javap.contains("ConstantValue: String helLO"), javap);
        int start = javap.indexOf("useMask(");
        String useMask = javap.substring(start, javap.indexOf("\n\n", start));
        assertTrue(useMask.contains("sipush        255") && !useMask.contains("getstatic"), "the constant is folded at use sites\n" + useMask);
        String clinit = javap.substring(javap.indexOf("static {}"));
        assertFalse(clinit.contains("Method crc:") || clinit.contains("Method mask:"), "no computation remains in the static initializer\n" + clinit);
    }

    private static Object field(Class<?> type, String name) throws Exception {
        java.lang.reflect.Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    @Test
    void siblingsFromTheSourceTreeAreCompiledThroughTheSourcePath() throws Exception {
        Path root = Files.createTempDirectory("consteval-src");
        Path pkg = Files.createDirectories(root.resolve("app/util"));
        Files.writeString(pkg.resolve("Sizes.java"), """
                package app.util;
                public final class Sizes { public static int kb(int n) { return n * 1024; } }
                """);
        Files.writeString(pkg.resolve("Config.java"), """
                package app.util;
                import dev.specialize.ConstEval;
                public class Config { @ConstEval public static final int BUFFER = Sizes.kb(64); }
                """);
        CompileHarness c = CompileHarness.compileFiles(List.of(pkg.resolve("Config.java"), pkg.resolve("Sizes.java")));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals(65536, c.load("app.util.Config").getField("BUFFER").get(null));

        assertEquals(Optional.of(root), ConstantEvaluator.sourceRoot(pkg.resolve("Config.java").toUri(), Optional.of("app.util")));
        assertEquals(Optional.of(pkg), ConstantEvaluator.sourceRoot(pkg.resolve("Config.java").toUri(), Optional.empty()));
        assertEquals(Optional.empty(), ConstantEvaluator.sourceRoot(pkg.resolve("Config.java").toUri(), Optional.of("other.util")));
        assertEquals(Optional.empty(), ConstantEvaluator.sourceRoot(Path.of("/Config.java").toUri(), Optional.of("app")));
        assertEquals(Optional.empty(), ConstantEvaluator.sourceRoot(URI.create("string:///t/X.java"), Optional.of("t")));
    }

    @Test
    void unreadableSourceIsReportedAtTheField() throws Exception {
        String text = "package t; import dev.specialize.ConstEval; public class X { @ConstEval static final int A = 1; }";
        javax.tools.JavaFileObject flaky = new javax.tools.SimpleJavaFileObject(URI.create("string:///t/X.java"), javax.tools.JavaFileObject.Kind.SOURCE) {
            private int reads;

            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws java.io.IOException {
                if (reads++ > 0) {
                    throw new java.io.IOException("disk gone");
                }
                return text;
            }
        };
        CompileHarness c = CompileHarness.compileObjects(List.of(flaky));
        assertFalse(c.success);
        assertTrue(c.errors().contains("@ConstEval A: cannot read the source file"), c.errors());
    }

    @Test
    void expressionsAreEvaluatedInPlace() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Exprs", """
                package t;
                import dev.specialize.Const;
                public class Exprs {
                    static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
                    public static int table() { return Const.eval(fib(20)) + 1; }
                    public static int[] pair() { return Const.eval(new int[]{fib(5), fib(6)}); }
                    public static String banner() { java.util.function.Supplier<String> s = () -> Const.eval("a" + "b".repeat(3)); return s.get(); }
                    public static class Inner { public static long big() { return dev.specialize.Const.eval(Long.parseLong("1" + "0".repeat(12))); } }
                    public static int wrongArity() { return Const.eval(1, 2); }
                }
                """));
        assertFalse(c.success, "Const.eval(1, 2) is javac's error; the side compilation reports it for every request");
        assertTrue(c.errors().contains("actual and formal argument lists differ in length"), c.errors());

        CompileHarness good = CompileHarness.compile(Map.of("t.Exprs", """
                package t;
                import dev.specialize.Const;
                public class Exprs {
                    static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
                    public static int table() { return Const.eval(fib(20)) + 1; }
                    public static int[] pair() { return Const.eval(new int[]{fib(5), fib(6)}); }
                    public static String banner() { java.util.function.Supplier<String> s = () -> Const.eval("a" + "b".repeat(3)); return s.get(); }
                    public static class Inner { public static long big() { return dev.specialize.Const.eval(Long.parseLong("1" + "0".repeat(12))); } }
                    public interface Api { static int size() { return Const.eval(3 * 4); } }
                    public static int untouched() { return eval(1) + Other.eval(3); }
                    static int eval(int x) { return x; }
                    static class Other { static int eval(int x) { return x; } }
                }
                """));
        assertTrue(good.success, good.allDiagnostics());
        assertEquals(4, good.load("t.Exprs").getMethod("untouched").invoke(null));
        Class<?> exprs = good.load("t.Exprs");
        assertEquals(6766, exprs.getMethod("table").invoke(null));
        assertArrayEquals(new int[]{5, 8}, (int[]) exprs.getMethod("pair").invoke(null));
        assertEquals("abbb", exprs.getMethod("banner").invoke(null));
        assertEquals(1_000_000_000_000L, good.load("t.Exprs$Inner").getMethod("big").invoke(null));
        assertEquals(12, good.load("t.Exprs$Api").getMethod("size").invoke(null));
        String javap = TailRecTest.javap(good, "t.Exprs");
        String table = javap.substring(javap.indexOf("table()"), javap.indexOf("pair()"));
        assertTrue(table.contains("sipush        6766") && !table.contains("invokestatic"), "fib(20) + 1 folded to a constant\n" + table);
        assertFalse(javap.contains("consteval"), "no hidden field in the real class\n" + javap);

        CompileHarness wrong = CompileHarness.compile(Map.of("t.Wrong", """
                package t;
                import dev.specialize.Const;
                public class Wrong {
                    static int local(int n) { return Const.eval(n + 1); }
                    static Object anonymous() { return new Object() { int f() { return Const.eval(1); } }; }
                    static int localClass() { class L { int f() { return Const.eval(2); } } return new L().f(); }
                    enum E { A; int f() { return Const.eval(3); } }
                }
                """, "t.NotLiteral", """
                package t;
                import dev.specialize.Const;
                public class NotLiteral { static Object object() { return Const.eval(new Object()); } }
                """));
        assertFalse(wrong.success);
        String errors = wrong.errors();
        assertTrue(errors.contains("Const.eval: cannot compile the class for evaluation") && errors.contains("variable n"), errors);
        assertEquals(3, errors.lines().filter(l -> l.contains("Const.eval: must be used inside a named class")).count(), errors);
        assertTrue(errors.contains("Const.eval: a value of type java.lang.Object cannot be written as a literal"), errors);
    }

    @Test
    void sideCompilationFailuresAreReported() throws Exception {
        var context = new com.sun.tools.javac.util.Context();
        javax.tools.JavaFileManager fm = javax.tools.ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null);
        context.put(javax.tools.JavaFileManager.class, fm);
        String text = "package t; class X { static final int A = 1; }";
        com.sun.tools.javac.tree.JCTree.JCCompilationUnit unit = com.sun.tools.javac.parser.ParserFactory.instance(context)
                .newParser(text, false, false, false).parseCompilationUnit();
        unit.sourcefile = new javax.tools.SimpleJavaFileObject(URI.create("string:///t/X.java"), javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        };
        try (ConstantEvaluator evaluator = new ConstantEvaluator(fm, Path.of("/nonexistent/root"))) {
            var result = evaluator.evaluate(unit, text, "t.X", "A");
            assertTrue(result instanceof dev.specialize.processor.consteval.EvaluationResult.Failure f
                    && f.message().contains("NoSuchFileException"), result.toString());
        }
    }

    @Test
    void closingALoaderThatFailsIsReported() {
        java.io.Closeable failing = () -> { throw new java.io.IOException("busy"); };
        java.io.UncheckedIOException failure = org.junit.jupiter.api.Assertions.assertThrows(java.io.UncheckedIOException.class,
                () -> ConstantEvaluator.closeQuietly(failing));
        assertEquals("busy", failure.getCause().getMessage());
    }

    @Test
    void errorsNameTheField() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Bad", """
                package t;
                import dev.specialize.ConstEval;
                public class Bad {
                    @ConstEval static int notFinal = 1;
                    @ConstEval static final int noInit;
                    @ConstEval static final Object OBJECT = new Object();
                    @ConstEval static final Object[] OBJECTS = {new Object()};
                    static { noInit = 1; }
                    void m() { Object o = new Object() { @ConstEval static final int IN_ANONYMOUS = 1; }; class Local { @ConstEval static final int IN_LOCAL = 2; } }
                    static final Runnable R = () -> { class InLambda { @ConstEval static final int X = 3; } };
                }
                """));
        assertFalse(c.success);
        String errors = c.errors();
        assertTrue(errors.contains("@ConstEval notFinal: the field must be static final"), errors);
        assertTrue(errors.contains("@ConstEval noInit: the field needs an initializer"), errors);
        assertTrue(errors.contains("@ConstEval OBJECT: a value of type java.lang.Object cannot be written as a literal"), errors);
        assertTrue(errors.contains("@ConstEval OBJECTS: a value of type [Ljava.lang.Object; cannot be written as a literal"), errors);
        CompileHarness throwing = CompileHarness.compile(Map.of("t.Throws", """
                package t;
                import dev.specialize.ConstEval;
                public class Throws { @ConstEval static final int THROWS = Integer.parseInt("x"); }
                """));
        assertFalse(throwing.success);
        assertTrue(throwing.errors().contains("@ConstEval THROWS: evaluation failed: java.lang.NumberFormatException: For input string: \"x\""), throwing.errors());
        assertTrue(errors.contains("@ConstEval IN_ANONYMOUS: must be a field of a named class"), errors);
        assertTrue(errors.contains("@ConstEval IN_LOCAL: must be a field of a named class"), errors);
        assertTrue(errors.contains("@ConstEval X: must be a field of a named class"), errors);

        CompileHarness broken = CompileHarness.compile(Map.of("t.Broken", """
                package t;
                import dev.specialize.ConstEval;
                public class Broken {
                    @ConstEval static final int A = Missing.value();
                    @ConstEval static final int B = 2;
                }
                """));
        assertFalse(broken.success);
        assertTrue(broken.errors().contains("@ConstEval A: cannot compile the class for evaluation:\n  line 4: cannot find symbol"), broken.errors());
        assertTrue(broken.errors().contains("@ConstEval B: cannot compile"), "one side compilation per file, reported for each field\n" + broken.errors());
    }
}
