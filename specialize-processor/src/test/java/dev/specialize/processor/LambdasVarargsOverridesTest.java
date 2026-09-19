package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LambdasVarargsOverridesTest {
    private static final String BOX = """
            package t;
            import dev.specialize.Specialize;
            @Specialize(types = {int.class, long.class})
            public class Box<T> {
                public T v;
                public static <T> Box<T> of(T v) { Box<T> b = new Box<>(); b.v = v; return b; }
                public static <T> Box<T> none() { Box<T> b = new Box<>(); return b; }
            }
            """;

    @Test
    void lambdasMethodReferencesAndVarargsAreRetargeted() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX, "t.Use", """
                package t;
                import java.util.function.*;
                public class Use {
                    static Supplier<Box<int>> field = () -> Box.none();
                    public static int run() {
                        Supplier<Box<int>> s = () -> Box.of(1);
                        Supplier<Box<int>> block = () -> { Runnable r = () -> { return; }; return Box.of(2); };
                        Supplier<Box<int>> ref = Box::none;
                        Supplier<Box<int>> other = Use::helper;
                        Function<String, Box<int>> f = str -> Box.of(3);
                        UnaryOperator<Box<int>> u = b -> Box.none();
                        Supplier<java.util.List<Box<int>>> list = () -> java.util.List.of(Box.of(1));
                        return s.get().v + block.get().v + f.apply("x").v + (u.apply(s.get()).v == 0 ? 0 : 100) + list.get().size()
                                + (ref.get().v == 0 ? 0 : 100) + supplied(() -> Box.of(4)).v + varargs(Box.of(5), Box.none()).v + varargs().v
                                + fixed(1, Box.none(), Box.of(6)).v + (returned().get().v == 0 ? 0 : 100) + two(1).v + other.get().v;
                    }
                    static Box<int> helper() { return Box.of(8); }
                    static Box<int> two(int a) { return Box.of(a); }
                    static Box<int> two(int a, int b, Box<int>... boxes) { return boxes[0]; }
                    static Box<int> supplied(Supplier<Box<int>> s) { return s.get(); }
                    public static Box<int> varargs(Box<int>... boxes) { return boxes.length == 0 ? Box.of(0) : boxes[0]; }
                    public static Box<int> fixed(int k, Box<int>... boxes) { return boxes[k]; }
                    static Supplier<Box<int>> returned() { return () -> Box.none(); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals(1 + 2 + 3 + 0 + 1 + 0 + 4 + 5 + 0 + 6 + 0 + 1 + 8, c.load("t.Use").getMethod("run").invoke(null));

        CompileHarness client = CompileHarness.compile(Map.of("c.C", """
                package c;
                import t.Box;
                import t.Use;
                public class C { public static int run() { return Use.varargs(Box.none(), Box.of(9)).v + Use.fixed(0, Box.of(10)).v + Use.varargs().v; } }
                """), java.util.List.of(c.classes), java.util.List.of());
        assertTrue(client.success, client.allDiagnostics());
        assertEquals(0 + 10 + 0, client.load("c.C", c.classes).getMethod("run").invoke(null));
    }

    @Test
    void overridesOfGenericSupertypesKeepBoxedSignatures() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Base", """
                package t;
                public abstract class Base<T> { public abstract T get(); public abstract void set(T v); }
                """, "t.Marker", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = int.class)
                public interface Marker<T> { }
                """, "t.Box", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = int.class)
                public class Box<T> extends Base<T> implements Marker<T>, Comparable<Box<T>>, java.util.function.Supplier<T> {
                    T v;
                    @Override public T get() { return v; }
                    @Override public void set(T v) { this.v = v; }
                    public T raw() { return v; }
                    @Override public int compareTo(Box<T> o) { return 0; }
                    @Override public String toString() { return "" + v; }
                }
                """, "t.Use", """
                package t;
                public class Use {
                    public static int run() { Box<int> b = new Box<>(); b.set(41); Base<Integer> base = b; return base.get() + b.raw() - b.compareTo(b) - 40; }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals(42, c.load("t.Use").getMethod("run").invoke(null));
        Class<?> boxInt = c.load("t.BoxInt");
        assertEquals(Integer.class, boxInt.getMethod("get").getReturnType());
        assertEquals(int.class, boxInt.getMethod("raw").getReturnType());
        assertEquals(boxInt, boxInt.getMethod("compareTo", boxInt).getParameterTypes()[0]);
    }
}
