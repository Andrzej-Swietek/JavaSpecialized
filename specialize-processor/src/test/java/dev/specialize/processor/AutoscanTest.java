package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutoscanTest {
    private static final String BOX = """
            package t;
            import dev.specialize.Specialize;
            @Specialize(autoscan = true)
            public class Box<T> {
                public T v;
                public static <T> Box<T> of(T v) { Box<T> b = new Box<>(); b.v = v; return b; }
            }
            """;

    @Test
    void generatesOnlyWhatTheCompilationUses() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX, "t.BoxByte", """
                package t;
                import dev.specialize.Specialized;
                @Specialized(of = Box.class, type = byte.class)
                public class BoxByte { public byte v = 9; public static BoxByte of(byte b) { return new BoxByte(); } }
                """, "t.Use", """
                package t;
                import java.util.List;
                public class Use {
                    Box<int> field = Box.of(1);
                    public static byte bytes() { Box<byte> b = Box.of((byte) 1); return b.v; }
                    @SuppressWarnings("unchecked") public static Object casts() { Box<int> a = (Box<int>) (Object) new java.util.ArrayList<>(); Box<int> b = (Box<int>) (Object) new java.util.ArrayList<String>(); return a == b ? a : b; }
                    public static Box<long> longs() { Box<long> l = Box.of(2L); return l; }
                    public static List<Box<double>> doubles() { return List.of(Box.<double>of(3.0)); }
                    public static Object generic() { Box<List<String>> b = Box.of(List.of("s")); return b; }
                    public static Object string() { Box<String> b = Box.of("s"); return b; }
                    public static <U> Object typeVariable(U u) { Box<U> b = Box.of(u); return b; }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertTrue(c.generated("t.BoxInt"));
        assertTrue(c.generated("t.BoxLong"));
        assertTrue(c.generated("t.BoxDouble"));
        assertFalse(c.generated("t.BoxBoolean"), "not used anywhere");
        assertFalse(c.generated("t.BoxString"), "reference types are never inferred");
        assertFalse(c.generated("t.BoxByte"), "explicit specialization wins even when autoscan sees the type");
        assertEquals((byte) 9, c.load("t.Use").getMethod("bytes").invoke(null));
        Class<?> use = c.load("t.Use");
        assertEquals("t.BoxInt", use.getDeclaredField("field").getType().getName());
        assertEquals("t.BoxLong", use.getMethod("longs").invoke(null).getClass().getName());
        assertEquals("t.Box", use.getMethod("string").invoke(null).getClass().getName());
        assertEquals("t.Box", use.getMethod("generic").invoke(null).getClass().getName());
        assertEquals("t.BoxInt", c.load("t.Box").getMethod("of", int.class).getReturnType().getName());
    }

    @Test
    void listedTypesAreKeptAndLaterRoundsCanAddMore() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX.replace("@Specialize(autoscan = true)",
                "@Specialize(autoscan = true, types = {boolean.class, String.class})"), "t.Codec", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = {short.class})
                public class Codec<T> {
                    public static <T> Box<T> wrap(T v) { return Box.of(v); }
                }
                """, "t.Use", """
                package t;
                public class Use {
                    public static Object shorts() { return CodecShort.wrap((short) 1); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertTrue(c.generated("t.BoxBoolean"), "listed");
        assertTrue(c.generated("t.BoxString"), "listed reference type");
        assertTrue(c.generated("t.BoxShort"), "discovered in the generated CodecShort during the second round");
        String codecShort = c.generatedSource("t.CodecShort");
        assertTrue(codecShort.contains("public static t.BoxShort wrap(short v)"), codecShort);
        assertEquals("t.BoxShort", c.load("t.Use").getMethod("shorts").invoke(null).getClass().getName());
        // bridges injected in round one are not copied into specializations generated in round two
        String boxShort = c.generatedSource("t.BoxShort");
        assertFalse(boxShort.contains("BoxBoolean"), boxShort);
    }

    @Test
    void usagesInAnotherCompilationFallBackToTheGenericClass() throws Exception {
        CompileHarness lib = CompileHarness.compile(Map.of("t.Box", BOX, "t.Lib", """
                package t;
                public class Lib { public static Box<int> ints() { return Box.of(1); } }
                """));
        assertTrue(lib.success, lib.allDiagnostics());
        CompileHarness client = CompileHarness.compile(Map.of("c.C", """
                package c;
                import t.Box;
                public class C {
                    public static Object floats() { Box<Float> f = Box.of(Float.valueOf(1f)); return f; }
                    public static Object ints() { Box<int> i = Box.of(1); return i; }
                }
                """), List.of(lib.classes), List.of());
        assertTrue(client.success, client.allDiagnostics());
        Class<?> c = client.load("c.C", lib.classes);
        assertEquals("t.Box", c.getMethod("floats").invoke(null).getClass().getName(), "boxed spelling is the generic class");
        assertEquals("t.BoxInt", c.getMethod("ints").invoke(null).getClass().getName());

        CompileHarness unsatisfiable = CompileHarness.compile(Map.of("c.D", """
                package c;
                import t.Box;
                public class D { Box<float> f; }
                """), List.of(lib.classes), List.of());
        assertFalse(unsatisfiable.success, "BoxFloat was never generated, so the primitive spelling is an error");
        assertTrue(unsatisfiable.errors().contains("primitive type argument on Box needs a @Specialize template"), unsatisfiable.errors());
    }
}
