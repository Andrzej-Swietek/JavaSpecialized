package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BoxedArgumentsPolicyTest {
    private static String box(String policy) {
        return """
                package t;
                import dev.specialize.BoxedArguments;
                import dev.specialize.Specialize;
                @Specialize(autoscan = true, boxedArguments = BoxedArguments.%s)
                public class Box<T> {
                    public T v;
                    public static <T> Box<T> of(T v) { Box<T> b = new Box<>(); b.v = v; return b; }
                }
                """.formatted(policy);
    }

    private static final String USE = """
            package t;
            import dev.specialize.Boxed;
            import java.util.List;
            public class Use {
                Box<int> primitive = Box.of(1);
                Box<Integer> boxed = Box.of(Integer.valueOf(2));
                @Boxed Box<Integer> optedOut = Box.of(Integer.valueOf(3));
                Box<Long> boxedLong;
            }
            """;

    @Test
    void specializeIsTheDefault() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", box("SPECIALIZE").replace(", boxedArguments = BoxedArguments.SPECIALIZE", ""), "t.Use", USE));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("t.BoxInt", c.load("t.Use").getDeclaredField("boxed").getType().getName());
    }

    @Test
    void keepReservesTheSpecializationForThePrimitiveSpelling() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", box("KEEP"), "t.Use", USE));
        assertTrue(c.success, c.allDiagnostics());
        Class<?> use = c.load("t.Use");
        assertEquals("t.BoxInt", use.getDeclaredField("primitive").getType().getName());
        assertEquals("t.Box", use.getDeclaredField("boxed").getType().getName());
        assertEquals("t.Box", use.getDeclaredField("optedOut").getType().getName());
        assertTrue(c.generated("t.BoxInt"));
        assertTrue(!c.generated("t.BoxLong"), "autoscan under KEEP only counts the primitive spelling");
    }

    @Test
    void specializeRewritesBoxedSpellingUnlessOptedOut() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", box("SPECIALIZE"), "t.Use", USE));
        assertTrue(c.success, c.allDiagnostics());
        Class<?> use = c.load("t.Use");
        assertEquals("t.BoxInt", use.getDeclaredField("primitive").getType().getName());
        assertEquals("t.BoxInt", use.getDeclaredField("boxed").getType().getName());
        assertEquals("t.Box", use.getDeclaredField("optedOut").getType().getName());
        assertTrue(c.generated("t.BoxLong"), "autoscan under SPECIALIZE counts Box<Long> too");
    }
}
