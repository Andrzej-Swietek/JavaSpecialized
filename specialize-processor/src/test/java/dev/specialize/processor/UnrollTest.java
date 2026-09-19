package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class UnrollTest {

    @Test
    void literalBoundLoopsBecomeStraightLineCode() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.U", """
                package t;
                import dev.specialize.Unroll;
                public class U {
                    static int i(int x) { return x + 100; }
                    static int n = 2;
                    static boolean flag;
                    @Unroll public static int dot(int[] a, int[] b) {
                        int sum = 0;
                        for (int i = 0; i < 4; i++) { sum += a[i] * b[i]; }
                        return sum;
                    }
                    @Unroll public static String shapes() {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 3; i >= 0; i--) sb.append(i);
                        for (int i = 0; i <= 6; i += 3) sb.append(',').append(i);
                        for (int i = 10; i > 4; i -= 2) sb.append(';').append(i);
                        for (int i = -2; i != (2); ++i) sb.append('|').append(i);
                        outer: for (int i = 0; i < 2; i++) { for (int j = 0; j < 2; j++) { int k = 0; while (k < 2) { k++; if (k == 1) continue; sb.append('#').append(i).append(j); } } }
                        for (int i = 0; i < 2; i++) { int k = i(i); sb.append('!').append(k); }
                        for (int i = 0; i < 3; i++) { switch (i) { case 1: break; default: sb.append('$').append(i); } }
                        for (int i = 0; i < 2; i++) { Runnable r = () -> sb.append('~').append(i); r.run(); }
                        for (int i = -(2); i < 0; ++i) { int[] a = new int[1]; a[0] = i; int k; k = a[0]; k += 1; k++; sb.append('^').append(k); }
                        for (int i = 3; i > 1; --i) { for (int j = 0; j < n; j++) { sb.append('%').append(i).append(j); } }
                        for (int i = 0; i < 1; i++) { inner: while (flag) { continue inner; } Object o = new Object() { public String toString() { return "@" + i; } }; sb.append(o); }
                        return sb.toString();
                    }
                    @Unroll(max = 4) public static int small() { int s = 0; for (int i = 0; i < 4; i++) { s += i; } return s; }
                    @Unroll public static int nested() {
                        int s = 0;
                        for (int i = 0; i < 3; i++) { for (int j = 0; j < 2; j++) { s += i * j; } }
                        return s;
                    }
                    public static int untouched() { int s = 0; for (int i = 0; i < 3; i++) { s += i; } return s; }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        Class<?> u = c.load("t.U");
        assertEquals(1 * 5 + 2 * 6 + 3 * 7 + 4 * 8, u.getMethod("dot", int[].class, int[].class).invoke(null, new int[]{1, 2, 3, 4}, new int[]{5, 6, 7, 8}));
        assertEquals("3210,0,3,6;10;8;6|-2|-1|0|1#00#01#10#11!100!101$0$2~0~1^0^1%30%31%20%21@0", u.getMethod("shapes").invoke(null));
        assertEquals(6, u.getMethod("small").invoke(null));
        assertEquals(3, u.getMethod("nested").invoke(null));
        String javap = TailRecTest.javap(c, "t.U");
        String dot = javap.substring(javap.indexOf("dot("), javap.indexOf("shapes("));
        assertFalse(dot.contains("goto") || dot.contains("if_icmp"), "no loop left:\n" + dot);
        assertTrue(dot.contains("iconst_3"), dot);
        String untouched = javap.substring(javap.indexOf("untouched("));
        assertTrue(untouched.contains("goto") || untouched.contains("if_icmp"), "methods without @Unroll keep their loops:\n" + untouched);
    }

    @Test
    void refusesWhatCannotBeCopied() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Bad", """
                package t;
                import dev.specialize.Unroll;
                public class Bad {
                    static boolean flag;
                    static int n = 3;
                    @Unroll static void nothingToUnroll() {
                        for (;;) { break; }
                        for (int i = 0; i < 3;) { i++; }
                        for (int i = 0, j = 0; i < 3; i++) { }
                        for (long i = 0; i < 3; i++) { }
                        for (Integer i = 0; i < 3; i++) { }
                        for (int i = 0; flag; i++) { }
                        for (int i = 0; (i & 1) == 0; i++) { break; }
                        for (int i = 0; 3 > i; i++) { }
                        for (int i = 0; n > i; i++) { }
                        for (int i = 0; i < n; i++) { }
                        for (int i = n; i < 3; i++) { }
                        for (int i = 0; i < 3; i += n) { }
                        for (int i = 0; i < 3; i -= n) { }
                        for (int i = 0; i < 3; n++) { }
                        for (int i = 0; i < 3; n--) { }
                        for (int i = 0; i < 3; n += 1) { }
                        for (int i = 0; i < 3; n -= 1) { }
                        for (int i = 0; i < 3L; i++) { }
                        for (int i = 0; i < -n; i++) { }
                        for (int i = 0; i < ~n; i++) { }
                        for (int i = 0; i < 3; i *= 2) { }
                        for (int i = 0; i < 3; i++, n++) { }
                        for (int i = 0; i < 3; i = i + 1) { }
                        for (int i = 0; i < 3; i = 4) { }
                        int i;
                        for (i = 0; i < 3; i++) { }
                        for (int j; ; ) { break; }
                    }
                    @Unroll static void assigns() { for (int i = 0; i < 3; i++) { i = 2; } }
                    @Unroll static void compound() { for (int i = 0; i < 3; i++) { i += 2; } }
                    @Unroll static void increments() { for (int i = 0; i < 3; i++) { i++; } }
                    @Unroll static void negates() { for (int i = 0; i < 3; i++) { int x = -i; } }
                    @Unroll static void redeclares() { for (int i = 0; i < 3; i++) { { int i = 1; } } }
                    @Unroll static void redeclaresInClass() { for (int i = 0; i < 3; i++) { Object o = new Object() { int f() { int i = 5; return i; } }; } }
                    @Unroll static void redeclaresAsField() { for (int i = 0; i < 3; i++) { class L { int i; } } }
                    @Unroll static void breaks() { for (int i = 0; i < 3; i++) { if (i == 1) break; } }
                    @Unroll static void continues() { for (int i = 0; i < 3; i++) { if (i == 1) continue; } }
                    @Unroll static void labeledBreak() { out: for (int i = 0; i < 3; i++) { for (int j = 0; j < 2; j++) { break out; } } }
                    @Unroll static void labeledContinue() { out: for (int i = 0; i < 3; i++) { while (flag) { continue out; } } }
                    @Unroll static void otherLabel() { out: { for (int i = 0; i < 3; i++) { do { break; } while (flag); for (int k : new int[0]) { continue; } } } }
                    @Unroll static void tooMany() { for (int i = 0; i < 65; i++) { } }
                    @Unroll(max = 2) static void tooManyForMax() { for (int i = 0; i < 3; i++) { } }
                    @Unroll static void endless() { for (int i = 0; i < 3; i += 0) { } }
                    @Unroll static void wrongWay() { for (int i = 0; i > -1; i++) { } }
                }
                """));
        assertFalse(c.success);
        String errors = c.errors();
        assertTrue(errors.contains("@Unroll nothingToUnroll: no for loop with literal bounds to unroll"), errors);
        assertEquals(3, count(errors, "the loop variable i is assigned in the body"), errors);
        assertEquals(3, count(errors, "the loop variable i is redeclared in the body"), errors);
        assertEquals(2, count(errors, "a break leaves the loop"), errors);
        assertEquals(2, count(errors, "a continue targets the loop"), errors);
        assertEquals(3, count(errors, "runs more than 64 times"), errors);
        assertTrue(errors.contains("runs more than 2 times"), errors);
        assertEquals(1 + 3 + 3 + 2 + 2 + 3 + 1, errors.lines().filter(l -> l.startsWith("@Unroll")).count(), errors);
    }

    @Test
    void malformedMaxIsJavacsError() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Bad", """
                package t;
                import dev.specialize.Unroll;
                public class Bad { @Unroll(8) static int f() { int s = 0; for (int i = 0; i < 2; i++) { s += i; } return s; } }
                """));
        assertFalse(c.success);
        assertTrue(c.errors().contains("cannot find symbol") && !c.allDiagnostics().contains("ClassCastException"), c.allDiagnostics());
    }

    private static int count(String text, String needle) {
        return (int) text.lines().filter(l -> l.contains(needle)).count();
    }
}
