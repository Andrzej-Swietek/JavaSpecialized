package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

/** {@code @Specialize} on static generic methods: one overload per primitive next to the generic method. */
class MethodSpecializeTest {

    @Test
    void overloadsAreGeneratedNextToTheGenericMethod() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Arrays2", """
                package t;
                import dev.specialize.Prim;
                import dev.specialize.Specialize;
                import java.util.List;
                import java.util.function.BinaryOperator;
                import java.util.function.Predicate;
                public final class Arrays2 {
                    @Specialize(types = {int.class, long.class})
                    public static <T> T sum(T[] xs, BinaryOperator<T> plus, T zero) { T acc = zero; for (T x : xs) { acc = plus.apply(acc, x); } return acc; }
                    @Specialize(types = int.class)
                    public static <T> int indexOf(T[] xs, T v) { for (int i = 0; i < xs.length; i++) { if (Prim.eq(xs[i], v)) { return i; } } return -1; }
                    @Specialize(types = {int.class, String.class}, namePattern = "{Name}Of{Type}")
                    public static <T> String join(List<T> xs, Predicate<T> keep) { StringBuilder sb = new StringBuilder(); for (T x : xs) { if (keep.test(x)) { sb.append(Prim.str(x)); } } return sb.toString(); }
                    @Specialize(types = int.class)
                    public static <T> T first(String label, T[] xs) { return xs[0]; }
                    @Specialize(types = double.class)
                    public static <T> T[] twice(T[] xs) { T[] out = Prim.newArray(xs.length * 2); System.arraycopy(xs, 0, out, 0, xs.length); System.arraycopy(xs, 0, out, xs.length, xs.length); return out; }
                    public static String run() {
                        int s = sum(new int[]{1, 2, 3}, Integer::sum, 0);
                        long l = sum(new long[]{4L, 5L}, Long::sum, 0L);
                        String g = sum(new String[]{"a", "b"}, String::concat, "");
                        return s + ":" + l + ":" + g + ":" + indexOf(new int[]{7, 8}, 8) + ":" + joinOfInt(List.of(1, 2, 3), x -> x > 1)
                                + ":" + joinOfString(List.of("x", "y"), x -> !x.isEmpty()) + ":" + twice(new double[]{1.5}).length + ":" + first("f", new int[]{9});
                    }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        Class<?> arrays = c.load("t.Arrays2");
        assertEquals("6:9:ab:1:23:xy:2:9", arrays.getMethod("run").invoke(null));
        assertEquals(int.class, arrays.getMethod("sum", int[].class, IntBinaryOperator.class, int.class).getReturnType());
        assertEquals(long.class, arrays.getMethod("sum", long[].class, java.util.function.LongBinaryOperator.class, long.class).getReturnType());
        assertEquals(6, arrays.getMethod("sum", int[].class, IntBinaryOperator.class, int.class).invoke(null, new int[]{1, 2, 3}, (IntBinaryOperator) Integer::sum, 0));
        assertTrue(Arrays.stream(arrays.getMethods()).anyMatch(m -> m.getName().equals("joinOfInt")), "erasure would clash: renamed");
        assertEquals(double[].class, arrays.getMethod("twice", double[].class).getReturnType());
        String dump = TailRecTest.javap(c, "t.Arrays2");
        assertTrue(dump.contains("public static int indexOf(int[], int);"), dump);
    }

    @Test
    void refusesWhatCannotBeOverloaded() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Bad", """
                package t;
                import dev.specialize.Specialize;
                public class Bad {
                    @Specialize(types = int.class) public <T> T instanceMethod(T x) { return x; }
                    @Specialize(types = int.class) public static int noTypeVariable(int x) { return x; }
                    @Specialize(types = void.class) public static <T> T badType(T x) { return x; }
                }
                """, "t.Box", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = int.class)
                public class Box<T> {
                    @Specialize(types = int.class) public static <T> T id(T x) { return x; }
                }
                """));
        assertFalse(c.success);
        String errors = c.errors();
        assertEquals(2, errors.lines().filter(l -> l.contains("must be static and declare exactly one type parameter")).count(), errors);
        assertTrue(errors.contains("cannot specialize for type void"), errors);
        assertTrue(errors.contains("annotate the class or the method, not both"), errors);
    }
}
