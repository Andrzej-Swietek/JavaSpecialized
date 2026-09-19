package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** The auto-started javac plugin makes {@code Opt<int>} legal in every position, not only in locals. */
class PrimitiveSignaturesTest {
    private static final String BOX = """
            package t;
            import dev.specialize.Specialize;
            @Specialize(types = {int.class, long.class})
            public class Box<T> {
                public T v;
                public static <T> Box<T> of(T v) { Box<T> b = new Box<>(); b.v = v; return b; }
            }
            """;

    @Test
    void primitiveArgumentsInSignaturesFieldsAndInterfaces() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX, "t.Repo", """
                package t;
                public interface Repo {
                    Box<int> find(long id);
                    default Box<long> asLong(Box<int> b) { return Box.of((long) b.v); }
                }
                """, "t.Use", """
                package t;
                import java.util.List;
                public class Use implements Repo {
                    Box<int> field = Box.of(1);
                    Box<int>[] array = new Box[1];
                    List<Box<long>> nested = List.of(Box.<long>of(2L));
                    @Override public Box<int> find(long id) { return Box.of((int) id); }
                    public static Box<int> viaExplicit() { return Box.<int>of(3); }
                    public record Msg(Box<int> a, Box<long> b) { }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        Class<?> use = c.load("t.Use");
        assertEquals("t.BoxInt", use.getDeclaredField("field").getType().getName());
        assertEquals("t.BoxInt[]", use.getDeclaredField("array").getType().getTypeName());
        assertEquals("t.BoxInt", use.getMethod("find", long.class).getReturnType().getName());
        assertEquals("t.BoxInt", c.load("t.Repo").getMethod("find", long.class).getReturnType().getName());
        assertEquals("t.BoxLong", c.load("t.Repo").getMethod("asLong", c.load("t.BoxInt")).getReturnType().getName());
        assertEquals("t.BoxInt", use.getMethod("viaExplicit").invoke(null).getClass().getName());
        assertEquals("t.BoxLong", c.load("t.Use$Msg").getRecordComponents()[1].getType().getName());
    }

    @Test
    void pluginIsDiscoverableByNameAndStartsOnItsOwn() {
        SpecializePlugin plugin = new SpecializePlugin();
        assertEquals("Specialize", plugin.getName());
        assertTrue(plugin.autoStart());
    }

    @Test
    void primitiveArgumentsWithoutASpecializationAreErrorsNotSilentBoxing() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX.replace("public T v;", "public T v; java.util.List<int> inTemplate;"), "t.Use", """
                package t;
                import dev.specialize.Boxed;
                import java.util.List;
                public class Use {
                    List<int> notATemplate;
                    Box<short> notListed;
                    @Boxed Box<int> contradiction;
                    List<@Boxed Box<int>> nestedContradiction;
                    static Object explicit() { return Box.<short>of((short) 1); }
                }
                """));
        assertFalse(c.success);
        String errors = c.errors();
        assertTrue(errors.contains("primitive type argument on List needs a @Specialize template"), errors);
        assertTrue(errors.contains("primitive type argument on Box needs a @Specialize template with a specialization for that primitive"), errors);
        assertTrue(errors.contains("primitive type argument on Box contradicts @Boxed"), errors);
        // 5 in Use and 1 in the template source; the copies generated from the already reported template stay silent
        assertEquals(6, errors.lines().filter(l -> l.contains("primitive type argument")).count(), errors);
        assertTrue(c.allDiagnostics().contains("Use.java:5:"), "reported at the offending line: " + c.allDiagnostics());
    }
}
