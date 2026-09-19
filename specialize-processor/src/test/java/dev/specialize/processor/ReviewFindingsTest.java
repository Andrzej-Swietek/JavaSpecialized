package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Regressions for the findings of the independent review. */
class ReviewFindingsTest {

    @Test
    void valuelessReturnInASpecializedMethodIsJavacsError() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = int.class) public class Box<T> { public static <T> Box<T> of(T v) { return new Box<>(); } }
                """, "t.Use", """
                package t;
                public class Use { static Box<int> broken() { return; } }
                """));
        assertFalse(c.success);
        assertTrue(c.errors().contains("missing return value") && !c.allDiagnostics().contains("NullPointerException"), c.allDiagnostics());
    }
    private static final String BOX = """
            package t;
            import dev.specialize.Specialize;
            @Specialize(types = {int.class, long.class, double.class})
            public class Box<T> {
                public T v;
                public static <T> Box<T> of(T v) { Box<T> b = new Box<>(); b.v = v; return b; }
                public static <T> Box<T> none() { return new Box<>(); }
                public static <T> Box<T> all(T... vs) { return new Box<>(); }
                public static <T, U> Box<T> withExtra(T v, U u) { return of(v); }
                public static <T> Box<T> tagged(T v, java.util.List<String> tags) { return of(v); }
                public static <T> int count(T v) { return 1; }
                public static <T> void log(T v) { }
            }
            """;

    @Test
    void unlistedPrimitivesDoNotWidenIntoASpecialization() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX, "t.Use", """
                package t;
                public class Use {
                    public static String run() {
                        var c = Box.of('c'); var s = Box.of((short) 1); var f = Box.of(1.5f); var i = Box.of(1); var b = Box.of((byte) 1);
                        Box<Character> typed = Box.of('x');
                        Box<Character> extra = Box.withExtra('y', "u");
                        Box.log('z');
                        Object all = Box.all();
                        return c.getClass().getSimpleName() + s.getClass().getSimpleName() + f.getClass().getSimpleName()
                                + i.getClass().getSimpleName() + b.getClass().getSimpleName() + typed.v + all.getClass().getSimpleName();
                    }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("BoxBoxBoxBoxIntBoxxBox", c.load("t.Use").getMethod("run").invoke(null));
    }

    @Test
    void arrayInitializersAndAssignmentsThroughThisAndArrays() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX, "t.Use", """
                package t;
                import static t.Box.*;
                public class Use {
                    Box<int> f;
                    Box<int>[] arr = {Box.none(), none()};
                    Object obj;
                    static Use ref;
                    Use() { this.f = Box.none(); arr[0] = none(); Use other = this; other.obj = Box.none(); Use.ref = this; Use.ref.obj = Box.none(); }
                    Use(int x) { this(Box.none(), x); }
                    Use(Box<int> f, int x) { this.f = f; }
                    static Box<int> compute() { return none(); }
                    static Box<int> shadowing() { return compute(); }
                    static Object shadowed(boolean b) {
                        if (b) { Box<int> x = Box.none(); return x; }
                        Object x = null; x = Box.none(); return x;
                    }
                    public static String run() { return new Use().f.getClass().getSimpleName() + new Use(1).f.getClass().getSimpleName()
                            + new Use().arr[1].getClass().getSimpleName() + shadowing().getClass().getSimpleName() + shadowed(false).getClass().getSimpleName(); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("BoxIntBoxIntBoxIntBoxIntBox", c.load("t.Use").getMethod("run").invoke(null));
    }

    @Test
    void inlineOverloadsPurityAndVisibility() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.M", """
                package t;
                import dev.specialize.Inline;
                import static java.lang.Math.max;
                public class M {
                    @Inline public static int f(int x) { return x * x; }
                    public static int f(long x) { return -1; }
                    @Inline public static int zero(int ignored) { return 0; }
                    @Inline public static int biggest(int a, int b) { return max(a, b); }
                    @Inline public static <T> T cast(Object o) { return (T) o; }
                    static int hidden(int x) { return x; }
                    @Inline public static int usesHidden(int x) { return hidden(x); }
                    public static int run(int[] arr) { return f(5L) + zero(arr.length) + biggest(1, 2) + M.<Integer>cast(3); }
                }
                """));
        assertFalse(c.success);
        assertTrue(c.errors().contains("t.M.usesHidden: references non-public member 'hidden'"), c.errors());

        CompileHarness ok = CompileHarness.compile(Map.of("t.M", """
                package t;
                import dev.specialize.Inline;
                import static java.lang.Math.max;
                public class M {
                    @Inline public static int f(int x) { return x * x; }
                    public static int f(long x) { return -1; }
                    @Inline public static int zero(int ignored) { return 0; }
                    @Inline public static int biggest(int a, int b) { return max(a, b); }
                    @Inline public static <T> T cast(Object o) { return (T) o; }
                    public static int run(int[] arr) { try { zero(arr[5]); return -1; } catch (ArrayIndexOutOfBoundsException e) { return f(5L) + biggest(1, 2) + M.<Integer>cast(3) + zero(arr.length); } }
                }
                """));
        assertTrue(ok.success, ok.allDiagnostics());
        assertEquals(-1 + 2 + 3 + 0, ok.load("t.M").getMethod("run", int[].class).invoke(null, new int[0]));
    }

    @Test
    void generatedCodeHonoursThisReceiversShadowingAndNonTPrimOperands() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Fn", """
                package t;
                import dev.specialize.Prim;
                import dev.specialize.Specialize;
                import java.util.function.Supplier;
                @Specialize(types = {int.class, String.class})
                public class Fn<T> {
                    private final Supplier<T> sup; private final String name; private final T[] values;
                    public String locale() { return Prim.str(java.util.Locale.ROOT) + Prim.str((Object) name); }
                    public Fn(Supplier<T> sup, String name, T[] values) { this.sup = sup; this.name = name; this.values = values; }
                    public T viaThis() { return this.sup.get(); }
                    public Supplier<T> ref() { return this.sup::get; }
                    public String describe(Supplier<String> sup) { return sup.get(); }
                    public boolean sameName(Fn<T> o) { return Prim.eq(name, o.name) && Prim.eq(values[0], o.values[0]) && Prim.hash(this.name) == Prim.hash(o.name); }
                    public boolean sameValue(Fn<T> o) { return Prim.eq(sup.get(), o.sup.get()); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        String fnInt = c.generatedSource("t.FnInt");
        assertTrue(fnInt.contains("return this.sup.getAsInt();"), fnInt);
        assertTrue(fnInt.contains("return this.sup::getAsInt;"), fnInt);
        assertTrue(fnInt.contains("return sup.get();"), fnInt);
        assertTrue(fnInt.contains("Prim.eq(name, o.name) && (values[0] == o.values[0]) && Prim.hash(this.name) == Prim.hash(o.name)"), fnInt);
        assertTrue(fnInt.contains("(sup.getAsInt() == o.sup.getAsInt())"), fnInt);
    }

    @Test
    void boxedParametersInheritedCalleesAndUnresolvableLiterals() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", BOX.replace("double.class})", "double.class}, boxedArguments = dev.specialize.BoxedArguments.SPECIALIZE)"), "t.Sup", """
                package t;
                public class Sup { public static Box<int> m(Box<int> b) { return b; } }
                """, "t.Use", """
                package t;
                import dev.specialize.Boxed;
                public class Use extends Sup {
                    static Object take(@Boxed Box<Integer> b, int x) { return b; }
                    public static String run() { return take(Box.none(), 1).getClass().getSimpleName() + Use.m(Box.none()).getClass().getSimpleName() + m(Box.none()).getClass().getSimpleName(); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("BoxBoxIntBoxInt", c.load("t.Use").getMethod("run").invoke(null));

        CompileHarness lib = CompileHarness.compile(Map.of("t.Box", BOX, "t.Sup", """
                package t;
                public class Sup { public static Box<int> m(Box<int> b) { return b; } }
                """));
        assertTrue(lib.success, lib.allDiagnostics());
        CompileHarness client = CompileHarness.compile(Map.of("c.Sub", """
                package c;
                import t.Box;
                public class Sub extends t.Sup { public static Object run() { return Sub.m(Box.none()); } }
                """), List.of(lib.classes), List.of());
        assertTrue(client.success, client.allDiagnostics());

        CompileHarness typo = CompileHarness.compile(Map.of("t.X", """
                package t;
                import dev.specialize.Specialized;
                @Specialized(of = Typo.class, type = int.class) public class X { }
                """, "t.Y", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = Missing.class) public class Y<T> { }
                """, "t.Z", """
                package t;
                import dev.specialize.SpecializeWith;
                @SpecializeWith(Gone.class) public class Z { }
                """));
        assertFalse(typo.success);
        assertFalse(typo.allDiagnostics().contains("uncaught exception"), typo.allDiagnostics());
    }

    @Test
    void referenceOnlyTemplatesAndUnsatisfiableCompositionsAreReportedAtTheTemplate() throws Exception {
        CompileHarness refs = CompileHarness.compile(Map.of("t.Box", BOX.replace("{int.class, long.class, double.class}", "String.class"), "t.Use", """
                package t;
                public class Use { public static Object run() { return Box.of("s"); } }
                """));
        assertTrue(refs.success, refs.allDiagnostics());
        assertEquals("t.BoxString", refs.load("t.Use").getMethod("run").invoke(null).getClass().getName());

        CompileHarness composition = CompileHarness.compile(Map.of("t.Box", BOX.replace("{int.class, long.class, double.class}", "int.class"), "t.Codec", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = {int.class, long.class})
                public class Codec<T> { public static <T> Box<T> wrap(T v) { return Box.of(v); } }
                """));
        assertFalse(composition.success);
        assertTrue(composition.errors().contains("primitive type argument on Box needs a @Specialize template"), composition.allDiagnostics());
    }

    @Test
    void librariesCompiledWithoutTheProcessorFallBackToTheGenericClass() throws Exception {
        CompileHarness lib = CompileHarness.compileUnprocessed(Map.of("lib.Box", """
                package lib;
                import dev.specialize.Specialize;
                @Specialize(types = int.class, boxedArguments = dev.specialize.BoxedArguments.SPECIALIZE) public class Box<T> { public T v; }
                """), List.of());
        assertTrue(lib.success, lib.allDiagnostics());
        CompileHarness c = CompileHarness.compile(Map.of("c.Use", """
                package c;
                import lib.Box;
                public class Use { public static Object run() { Box<Integer> b = new Box<>(); return b; } }
                """), List.of(lib.classes), List.of());
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("lib.Box", c.load("c.Use", lib.classes).getMethod("run").invoke(null).getClass().getName(), "no BoxInt was ever generated");
    }

    @Test
    void leftoverPrimitiveMarkersAreErrorsEvenWithoutTheProcessor() throws Exception {
        CompileHarness c = CompileHarness.compileUnprocessed(Map.of("t.Use", """
                package t;
                public class Use { java.util.List<int> l = java.util.List.of(1); }
                """), List.of());
        assertFalse(c.success);
        assertTrue(c.errors().contains("primitive type argument was not rewritten: the specialize annotation processor did not run"), c.allDiagnostics());
    }
}
