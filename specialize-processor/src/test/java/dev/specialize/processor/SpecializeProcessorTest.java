package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpecializeProcessorTest {

    private static final String BOX = """
            package t;
            import dev.specialize.Prim;
            import dev.specialize.Specialize;
            @Specialize(types = {int.class, char.class, byte.class, short.class, float.class})
            public final class Box<T> implements Comparable<Box<T>> {
                private T value;
                private static final Box<?> NONE = new Box<>(Prim.zero());
                public Box(T value) { this.value = value; }
                public static <T> Box<T> of(T value) { return new Box<>(value); }
                public static <T> Box<T> none() { return (Box<T>) NONE; }
                public static <T> Box<T>[] many(int n) { Box<T>[] boxes = new Box[n]; return boxes; }
                public T get() { return value; }
                public void set(T value) { this.value = value; }
                public T[] pair() { T[] a = Prim.newArray(2); a[0] = value; a[1] = value; return a; }
                public boolean is(T other) { return Prim.eq(value, other); }
                public java.util.List<T[]> arrays() { return java.util.Collections.singletonList(pair()); }
                @Override public int compareTo(Box<T> o) { return Prim.compare(value, o.value); }
                @Override public int hashCode() { return Prim.hash(value); }
                @Override public String toString() { return "Box(" + Prim.str(value) + ")"; }
            }
            """;

    @Test
    void specializesForEveryPrimitiveKindIncludingTheOddOnes() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX, "t.Use", """
                package t;
                public class Use {
                    public static Box<char> chars() { return Box.of('x'); }
                    public static Box<byte> bytes() { Box<byte> local = Box.of((byte) 3); return local; }
                    public static Box<short> shorts() { return Box.none(); }
                    public static Box<float> floats() { Box<float> f = new Box<>(1.5f); return f; }
                    public static Box<int>[] ints() { return Box.many(2); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        String boxByte = c.generatedSource("t.BoxByte");
        assertTrue(boxByte.contains("new BoxByte((byte)0)"), boxByte);
        assertTrue(boxByte.contains("byte[] a = new byte[2]"), boxByte);
        assertTrue(boxByte.contains("java.lang.Byte.compare(value, o.value)"), boxByte);
        assertTrue(boxByte.contains("public static BoxByte[] many(int n) {\n        BoxByte[] boxes = new BoxByte[n];"), boxByte);
        assertTrue(boxByte.contains("implements Comparable<BoxByte>"), boxByte);
        assertTrue(boxByte.contains("java.util.List<byte[]> arrays()"), boxByte);
        String boxChar = c.generatedSource("t.BoxChar");
        assertTrue(boxChar.contains("(value == other)"), boxChar);

        Class<?> use = c.load("t.Use");
        assertEquals("t.BoxChar", use.getMethod("chars").getReturnType().getName());
        assertEquals("t.BoxByte", use.getMethod("bytes").getReturnType().getName());
        assertEquals("t.BoxShort", use.getMethod("shorts").getReturnType().getName());
        assertEquals("t.BoxFloat", use.getMethod("floats").getReturnType().getName());
        assertEquals("t.BoxInt[]", use.getMethod("ints").getReturnType().getTypeName());
        Object chars = use.getMethod("chars").invoke(null);
        assertEquals('x', chars.getClass().getMethod("get").invoke(chars));
        assertEquals(char.class, chars.getClass().getMethod("get").getReturnType());
        Method bridge = c.load("t.Box").getMethod("of", char.class);
        assertEquals("t.BoxChar", bridge.getReturnType().getName());
    }

    @Test
    void bareAnnotationSpecializesForAllEightPrimitives() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.All", """
                package t;
                import dev.specialize.Specialize;
                @Specialize public class All<T> { public T v; }
                """));
        assertTrue(c.success, c.allDiagnostics());
        for (String suffix : java.util.List.of("Int", "Long", "Double", "Boolean", "Byte", "Short", "Char", "Float")) {
            assertTrue(c.generated("t.All" + suffix), suffix);
        }
    }

    @Test
    void rejectsNestedTemplatesAndWrongArity() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Bad", """
                package t;
                import dev.specialize.Specialize;
                public class Bad {
                    @Specialize static class Nested<T> { }
                }
                """, "t.Pair", """
                package t;
                import dev.specialize.Specialize;
                @Specialize public class Pair<K, V> { }
                """));
        assertFalse(c.success);
        assertTrue(c.errors().contains("only top-level classes"), c.errors());
        assertTrue(c.errors().contains("mark the ones to specialize with @Specialize.Param"), c.errors());
    }

    @Test
    void rejectsClashWithAnUnmarkedHandWrittenClass() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX, "t.BoxInt", """
                package t;
                public class BoxInt { }
                """));
        assertFalse(c.success);
        assertTrue(c.errors().contains("t.BoxInt already exists"), c.errors());
        assertTrue(c.errors().contains("@Specialized(of = Box.class, type = int.class)"), c.errors());
    }

    @Test
    void explicitSpecializationInSameCompilationReplacesGeneratedOne() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX, "t.BoxInt", """
                package t;
                import dev.specialize.Specialized;
                @Specialized(of = Box.class, type = int.class)
                public final class BoxInt {
                    private final int v;
                    public BoxInt(int v) { this.v = v; }
                    public static BoxInt of(int v) { return new BoxInt(v); }
                    public int get() { return v; }
                    public int twice() { return v * 2; }
                }
                """, "t.Use", """
                package t;
                public class Use {
                    public static int run() { Box<int> b = Box.of(21); return b.twice(); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals(42, c.load("t.Use").getMethod("run").invoke(null));
        assertTrue(java.nio.file.Files.notExists(c.generatedSources.resolve("t/BoxInt.java")));
    }

    @Test
    void rejectsInlineMethodsItCannotExpandSafely() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.M", """
                package t;
                import dev.specialize.Inline;
                public class M {
                    private static int secret = 1;
                    private static int secret2 = 1;
                    @Inline int instance(int x) { return x; }
                    @Inline static int twoStatements(int x) { int y = x; return y; }
                    @Inline static void loop(int x) { while (x > 0) x--; }
                    @Inline static int varargs(int... xs) { return xs.length; }
                    @Inline static java.util.function.IntSupplier lambda(int x) { return () -> x; }
                    @Inline static int usesPrivate(int x) { return x + secret + secret2; }
                    @Inline static int unknown(int x) { return x + whatever + whatever2; }
                }
                """));
        assertFalse(c.success);
        String errors = c.errors();
        assertTrue(errors.contains("t.M.instance: only static methods"), errors);
        assertFalse(errors.contains("twoStatements") || errors.contains("t.M.loop"), "block bodies are inlined now: " + errors);
        assertTrue(errors.contains("t.M.varargs: varargs methods cannot be inlined"), errors);
        assertTrue(errors.contains("t.M.lambda: bodies containing lambdas"), errors);
        assertTrue(errors.contains("t.M.usesPrivate: references non-public member 'secret'"), errors);
        assertTrue(errors.contains("t.M.unknown: references 'whatever'"), errors);
    }

    @Test
    void inlinesAcrossFilesWithStaticImportsAndNestedCalls() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.M", """
                package t;
                import dev.specialize.Inline;
                public class M {
                    public static final int K = 10;
                    @Inline public static int sq(int x) { return x * x; }
                    @Inline public static int scaled(int x) { return sq(x) * K; }
                    @Inline public static double ratio(double a, double b) { return a / b; }
                    @Inline public static <T> T first(java.util.List<T> l) { return l.get(0); }
                }
                """, "t.Use", """
                package t;
                import static t.M.scaled;
                import static t.M.*;
                public class Use {
                    public static final int FOLDED = scaled(3);
                    public static double r() { return ratio(1, 4); }
                    public static String f() { return first(java.util.List.of("a")); }
                    public static int viaClass(int v) { return M.sq(v) + sq(v); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        Class<?> use = c.load("t.Use");
        assertEquals(90, use.getField("FOLDED").get(null));
        assertEquals(0.25, use.getMethod("r").invoke(null), "arguments cast to double: 1 / 4 is not integer division");
        assertEquals("a", use.getMethod("f").invoke(null));
        assertEquals(50, use.getMethod("viaClass", int.class).invoke(null, 5));
        // FOLDED is a compile-time constant: ConstantValue attribute, so reading it needs no class initialisation
        assertTrue(java.lang.reflect.Modifier.isFinal(use.getField("FOLDED").getModifiers()));
        byte[] bytes = java.nio.file.Files.readAllBytes(c.classes.resolve("t/Use.class"));
        assertFalse(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains("scaled"),
                "no reference to M.scaled remains in Use.class");
    }
}
