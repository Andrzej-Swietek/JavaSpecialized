package dev.specialize.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VecTest {

    @Test
    void primitiveOverloadsExistNextToTheGenericMethod() throws Exception {
        assertEquals(6, Vec.fold(new int[]{1, 2, 3}, 0, Integer::sum));
        assertEquals(9L, Vec.fold(new long[]{4, 5}, 0L, Long::sum));
        assertEquals(1.5, Vec.fold(new double[]{1, 0.5}, 0.0, Double::sum));
        assertEquals("ab", Vec.fold(new String[]{"a", "b"}, "", String::concat));
        assertEquals(1, Vec.indexOf(new int[]{7, 8}, 8));
        assertEquals(-1, Vec.indexOf(new int[]{7, 8}, 9));
        assertEquals(-1, Vec.indexOf(new double[]{7}, 8));
        assertEquals(1, Vec.indexOf(new double[]{7, 8}, 8));
        assertEquals(0, Vec.indexOf(new String[]{"x"}, "x"));
        assertEquals(-1, Vec.indexOf(new String[]{"x"}, "y"));
        assertEquals("<1> <2>", Vec.describeInt(x -> "<" + x + ">", java.util.List.of(1, 2)));
        assertEquals("a", Vec.describe(String::toString, java.util.List.of("a")));

        int[] data = {1, 2, 3};                                  // a local: `new int[]{…}` as an argument is not pure, the call would be kept
        assertEquals(14, Vec.sumOf(data, x -> x * x));
        int[] seen = {0};
        Vec.each(data, x -> seen[0] += x);
        assertEquals(6, seen[0]);
        String caller = Bytecode.of(VecTest.class);
        String sumOf = String.join("", "Method dev/specialize/examples/Vec.sum", "Of:(");   // not a literal of this class
        String each = String.join("", "Method dev/specialize/examples/Vec.ea", "ch:(");
        assertFalse(caller.contains(sumOf) || caller.contains(each), "inlined with the lambdas applied\n" + caller);

        // the bodies themselves only run when called reflectively: every source-level call is inlined
        assertEquals(14, Vec.class.getMethod("sumOf", int[].class, java.util.function.IntUnaryOperator.class)
                .invoke(null, data, (java.util.function.IntUnaryOperator) x -> x * x));
        Vec.class.getMethod("each", int[].class, java.util.function.IntConsumer.class).invoke(null, data, (java.util.function.IntConsumer) x -> seen[0] += x);
        assertEquals(12, seen[0]);

        String javap = Bytecode.of(Vec.class);
        assertTrue(javap.contains("public static int fold(int[], int, java.util.function.IntBinaryOperator);"), javap);
        assertTrue(javap.contains("public static int indexOf(double[], double);"), javap);
        assertTrue(javap.contains("public static java.lang.String describeInt(java.util.function.IntFunction<java.lang.String>, java.util.List<java.lang.Integer>);"), javap);
    }
}
