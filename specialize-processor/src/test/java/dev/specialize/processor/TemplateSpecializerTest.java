package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateSpecializerTest {

    private static final String FN = """
            package t;
            import dev.specialize.Prim;
            import dev.specialize.Specialize;
            import java.io.*;
            import java.nio.ByteBuffer;
            import java.util.function.*;
            @Specialize(types = {int.class, long.class, double.class, boolean.class, char.class, byte.class, float.class, short.class})
            public final class Fn<T> {
                private final Supplier<T> sup;
                private final Function<T, String> toStr;
                private final Function<String, T> parse;
                Fn(Supplier<T> sup, Function<T, String> toStr, Function<String, T> parse) { this.sup = sup; this.toStr = toStr; this.parse = parse; }
                public T supply() { return sup.get(); }
                public String show() { return toStr.apply(sup.get()); }
                public T parse(String s) { return parse.apply(s); }
                public T same(java.util.function.Function<T, T> f, T v) { return f.apply(v); }
                public T reduce(BinaryOperator<T> op, T a, T b) { return op.apply(a, b); }
                public T unary(UnaryOperator<T> op, T a) { UnaryOperator<T> local = op; return local.apply(a); }
                public boolean test(Predicate<T> p, T v) { return p.test(v); }
                public void each(Consumer<T> c, T v) { c.accept(v); }
                public <R extends T> R bound(R r) { return r; }
                @SuppressWarnings("unchecked") public T cast(Object o) { return (T) o; }
                public java.util.List<? extends T> wild(java.util.List<? extends T> l) { return l; }
                public boolean same(T a, T b) { return Prim.eq(a, b) && Prim.compare(a, b) == 0 && Prim.hash(a) == Prim.hash(b) && Prim.str(a).equals(Prim.str(b)); }
                public boolean nul(T a) { return Prim.isNull(a) || !Prim.isPrimitive() || Prim.type() == null || Prim.box(a) == null; }
                public void io(DataOutput out, DataInput in, ByteBuffer buf, T v) throws IOException {
                    Prim.write(out, v); T r = Prim.<T>read(in); Prim.put(buf, r); T g = Prim.<T>get(buf); int n = Prim.bytes();
                }
                public T[] two(T v) { T[] a = Prim.newArray(2); a[0] = v; a[1] = Prim.zero(); return a; }
                public static <T> Fn<T> of(Supplier<T> s, Function<T, String> f, Function<String, T> p) { return new Fn<>(s, f, p); }
                public static <T> Fn<T> pick(boolean b, Fn<T> x, Fn<T> y) { return b ? (x) : y; }
                public static <T> Fn<T> viaCast(Object o) { return (Fn<T>) o; }
                @SuppressWarnings({"unchecked", "rawtypes"}) public static <T> Fn<T> raw(Supplier<T> s, Function<T, String> f, Function<String, T> p) { return new Fn(s, f, p); }
                public static <T> Fn<T>[] many(int n) { Fn<T>[] a = new Fn[n]; return a; }
                public static <T> Fn<T> explicit(Fn<T> f) { return Fn.<T>pick(true, f, f); }
                public static <T> Fn<T> lambdaInside(Fn<T> f) { Supplier<Object> s = () -> { return null; }; Runnable r = () -> { Fn<T> g = Fn.<T>pick(false, f, f); }; return f; }
                public static <T> void log(T v) { System.out.println(Prim.str(v)); }
                public static <T> Fn<T> arrays(T[] values, Fn<T> f) { return f; }
                public static <T> int count(T v) { return 1; }
                public static <T, U> Fn<T> withExtra(T v, U u, Fn<T> f) { return f; }
                public static <U> U other(U u) { return u; }
                public static <R> Fn<R> otherTyped(Fn<R> f) { return Fn.<R>pick(true, f, f); }
                public static <T> Fn<T> viaStatic(Fn<T> f) { return Fn.pick(true, f, f); }
                public static <T> Fn<T> fullyQualified(t.Fn<T> f) { return f; }
                public static <T> Fn<T> chain(Fn<T> f) { return f.self().self(); }
                public static <T> Fn<T> nonDiamond(Supplier<T> s, Function<T, String> f, Function<String, T> p) { return new Fn<T>(s, f, p); }
                @SuppressWarnings("unchecked") public static <T> Fn<T> viaObjectCast() { return (Fn<T>) (Object) new java.util.ArrayList<>(); }
                @SuppressWarnings("unchecked") public static <T> Fn<T>[] pair(Fn<T> a) { Fn<T>[] arr2 = (Fn<T>[]) new Object[0]; return arr2; }
                public static <T> int mapParam(java.util.Map<T, T> m, java.util.List<? extends T> l, Nested<T> n) { return 0; }
                public static <T> Fn<T> lst(java.util.List<String> l, java.util.List<? extends String> w, Fn<T> f) { return f; }
                @SuppressWarnings("unchecked") public static <T> Fn<T> viaObjectCast2() { return (Fn<T>) (Object) new java.util.ArrayList<String>(); }
                public static native <T> void nat(T v);
                public static boolean isFn(Object o) { return o instanceof Fn<?>; }
                public static boolean isNum(Fn<? extends Number> f) { return f != null; }
                public static <T> boolean wildPred(Predicate<? super T> p, T v) { return p.test(v); }
                public Fn<T> self() { return this; }
                public <U> U ident(U u) { return u; }
                public T viaThis(T v) { return this.<T>ident(v); }
                public <U> Fn<T> withU(U u) { return this; }
                public Fn<T> withStr() { return this.<String>withU("x"); }
                public String supStr() { var q = 1; return sup.toString() + q + dev.specialize.Prim.<T>zero(); }
                private Predicate<String> ps; private Consumer<String> cs; private Supplier<String> ss;
                private UnaryOperator<String> us; private BinaryOperator<String> bs; private Function<String, String> fs;
                public static class Nested<T> { T inner; }
            }
            """;

    @Test
    void mapsFunctionalInterfacesAndPrimHelpersForEveryPrimitive() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Fn", FN, "t.Use", """
                package t;
                public class Use {
                    public static int run() {
                        Fn<int> f = Fn.of(() -> 41, String::valueOf, Integer::parseInt);
                        FnLong l = Fn.of(() -> 1L, String::valueOf, Long::parseLong);
                        Fn<boolean> b = Fn.of(() -> true, String::valueOf, Boolean::parseBoolean);
                        Fn<char> ch = Fn.of(() -> 'c', String::valueOf, s -> s.charAt(0));
                        return f.same(x -> x + 1, f.supply()) + f.parse("0") + (int) l.supply() + (b.supply() ? 0 : 1) + (ch.supply() == 'c' ? 0 : 1) + f.two(1).length - 2;
                    }
                }
                """));
        assertTrue(c.success, c.allDiagnostics() + "\n" + c.generatedSource("t.FnInt"));
        String fnInt = c.generatedSource("t.FnInt");
        assertTrue(fnInt.contains("private final java.util.function.IntSupplier sup;"), fnInt);
        assertTrue(fnInt.contains("private final java.util.function.IntFunction<String> toStr;"), fnInt);
        assertTrue(fnInt.contains("private final java.util.function.ToIntFunction<String> parse;"), fnInt);
        assertTrue(fnInt.contains("return sup.getAsInt();"), fnInt);
        assertTrue(fnInt.contains("return toStr.apply(sup.getAsInt());"), fnInt);
        assertTrue(fnInt.contains("return parse.applyAsInt(s);"), fnInt);
        assertTrue(fnInt.contains("public int same(java.util.function.IntUnaryOperator f, int v) {\n        return f.applyAsInt(v);"), fnInt);
        assertTrue(fnInt.contains("public int reduce(java.util.function.IntBinaryOperator op, int a, int b) {\n        return op.applyAsInt(a, b);"), fnInt);
        assertTrue(fnInt.contains("java.util.function.IntUnaryOperator local = op;\n        return local.applyAsInt(a);"), fnInt);
        assertTrue(fnInt.contains("public boolean test(java.util.function.IntPredicate p, int v)"), fnInt);
        assertTrue(fnInt.contains("public static boolean wildPred(java.util.function.IntPredicate p, int v)"), fnInt);
        assertTrue(fnInt.contains("public static boolean isNum(Fn<? extends Number> f)"), fnInt);
        assertTrue(fnInt.contains("public void each(java.util.function.IntConsumer c, int v)"), fnInt);
        assertTrue(fnInt.contains("public <R extends java.lang.Integer>R bound(R r)"), fnInt);
        assertTrue(fnInt.contains("return (java.lang.Integer)o;"), fnInt);
        assertTrue(fnInt.contains("java.util.List<? extends java.lang.Integer> wild("), fnInt);
        assertTrue(fnInt.contains("(a == b) && java.lang.Integer.compare(a, b) == 0 && java.lang.Integer.hashCode(a) == java.lang.Integer.hashCode(b) && java.lang.String.valueOf(a).equals(java.lang.String.valueOf(b))"), fnInt);
        assertTrue(fnInt.contains("return false || !true || int.class == null || java.lang.Integer.valueOf(a) == null;"), fnInt);
        assertTrue(fnInt.contains("out.writeInt(v);"), fnInt);
        assertTrue(fnInt.contains("int r = in.readInt();"), fnInt);
        assertTrue(fnInt.contains("buf.putInt(r);"), fnInt);
        assertTrue(fnInt.contains("int g = buf.getInt();"), fnInt);
        assertTrue(fnInt.contains("int n = java.lang.Integer.BYTES;"), fnInt);
        assertTrue(fnInt.contains("int[] a = new int[2];"), fnInt);
        assertTrue(fnInt.contains("a[1] = 0;"), fnInt);
        assertTrue(fnInt.contains("return b ? (FnInt.pick(true, f, f)) : y;") || fnInt.contains("return b ? (x) : y;"), fnInt);
        assertTrue(fnInt.contains("return (FnInt)o;"), fnInt);
        assertTrue(fnInt.contains("return new FnInt(s, f, p);"), fnInt);
        assertTrue(fnInt.contains("FnInt[] a = new FnInt[n];"), fnInt);
        assertTrue(fnInt.contains("return FnInt.pick(true, f, f);"), fnInt);
        assertTrue(fnInt.contains("FnInt g = FnInt.pick(false, f, f);"), fnInt);
        assertTrue(fnInt.contains("public static class Nested<T> {"), fnInt);
        assertTrue(fnInt.contains("return FnInt.pick(true, f, f);"), fnInt);
        assertTrue(fnInt.contains("public static <R>Fn<R> otherTyped(Fn<R> f) {\n        return Fn.<R>pick(true, f, f);"), fnInt);
        assertTrue(fnInt.contains("public static FnInt fullyQualified(FnInt f)"), fnInt);
        assertTrue(fnInt.contains("return new FnInt(s, f, p);"), fnInt);
        assertTrue(fnInt.contains("return o instanceof FnInt;"), fnInt);
        assertTrue(fnInt.contains("return this.<java.lang.Integer>ident(v);"), fnInt);
        assertTrue(fnInt.contains("return this.<String>withU(\"x\");"), fnInt);
        assertTrue(fnInt.contains("return sup.toString() + q + 0;"), fnInt);
        assertTrue(fnInt.contains("private Predicate<String> ps;"), fnInt);
        assertTrue(fnInt.contains("public static <U>FnInt withExtra(int v, U u, FnInt f)"), fnInt);

        String fnBool = c.generatedSource("t.FnBoolean");
        assertTrue(fnBool.contains("java.util.function.BooleanSupplier sup;"), fnBool);
        assertTrue(fnBool.contains("Function<java.lang.Boolean, String> toStr;"), fnBool);
        assertTrue(fnBool.contains("Predicate<java.lang.Boolean> p"), fnBool);
        assertTrue(fnBool.contains("buf.put((byte)(r ? 1 : 0));"), fnBool);
        assertTrue(fnBool.contains("boolean g = (buf.get() != 0);"), fnBool);
        assertTrue(fnBool.contains("int n = 1;"), fnBool);
        assertTrue(fnBool.contains("a[1] = false;"), fnBool);

        String fnChar = c.generatedSource("t.FnChar");
        assertTrue(fnChar.contains("Supplier<java.lang.Character> sup;"), fnChar);
        assertTrue(fnChar.contains("out.writeChar(v);"), fnChar);
        String fnByte = c.generatedSource("t.FnByte");
        assertTrue(fnByte.contains("buf.put(r);"), fnByte);
        assertTrue(fnByte.contains("byte g = buf.get();"), fnByte);
        assertTrue(fnByte.contains("a[1] = (byte)0;"), fnByte);
        String fnDouble = c.generatedSource("t.FnDouble");
        assertTrue(fnDouble.contains("(java.lang.Double.compare(a, b) == 0)"), fnDouble);
        assertTrue(fnDouble.contains("a[1] = 0.0;"), fnDouble);
        String fnFloat = c.generatedSource("t.FnFloat");
        assertTrue(fnFloat.contains("a[1] = 0.0F;"), fnFloat);
        String fnLong = c.generatedSource("t.FnLong");
        assertTrue(fnLong.contains("a[1] = 0L;"), fnLong);
        String fnShort = c.generatedSource("t.FnShort");
        assertTrue(fnShort.contains("a[1] = (short)0;"), fnShort);

        assertEquals(42 + 0 + 1 + 0 + 0 + 0, c.load("t.Use").getMethod("run").invoke(null));
        // bridges: T[] and Fn<T> parameters change erasure, void factories are bridged too, Supplier<T> ones are not
        Class<?> fn = c.load("t.Fn");
        assertEquals("t.FnInt", fn.getMethod("arrays", int[].class, c.load("t.FnInt")).getReturnType().getName());
        assertEquals(void.class, fn.getMethod("log", long.class).getReturnType());
        assertEquals("t.FnInt", fn.getMethod("pick", boolean.class, c.load("t.FnInt"), c.load("t.FnInt")).getReturnType().getName());
        assertFalse(java.util.Arrays.stream(fn.getMethods()).anyMatch(m -> m.getName().equals("of") && m.getReturnType().getName().equals("t.FnInt")));
    }

    @Test
    void ownClassNamedPrimIsLeftAlone() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Prim", """
                package t;
                public class Prim { public static int magic() { return 7; } }
                """, "t.Box", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = int.class)
                public class Box<T> { T v; public int m() { return Prim.magic(); } }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertTrue(c.generatedSource("t.BoxInt").contains("return Prim.magic();"));
    }

    @Test
    void explicitSpecializationInAnotherPackageAndForAListedType() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("a.Box", """
                package a;
                import dev.specialize.Specialize;
                @Specialize(types = {int.class, String.class})
                public class Box<T> {
                    public final T v;
                    public Box(T v) { this.v = v; }
                    public static <T> Box<T> of(T v) { return new Box<>(v); }
                    public static <T> Box<T> twice(T v, T w) { return new Box<>(v); }
                }
                """, "b.BoxStr", """
                package b;
                import dev.specialize.Specialized;
                @Specialized(of = a.Box.class, type = String.class)
                public class BoxStr {
                    private final int marker = 1;
                    public static BoxStr of(String s, int n) { return new BoxStr(); }
                    public static BoxStr of(String s) { return new BoxStr(); }
                    public BoxStr twice(String a, String b) { return this; }
                    public String kind() { return "explicit"; }
                }
                """, "b.BoxUser", """
                package b;
                import dev.specialize.Specialized;
                @Specialized(of = a.Box.class, type = b.User.class)
                public class BoxUser {
                    public static BoxUser of(b.User u) { return new BoxUser(); }
                }
                """, "b.User", """
                package b;
                public record User(String n) {}
                """, "c.Use", """
                package c;
                import a.Box;
                public class Use {
                    public static String run() { Box<String> s = Box.of("x"); Box<b.User> u = Box.of(new b.User("n")); return s.kind() + u.getClass().getSimpleName(); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertFalse(c.generated("a.BoxString"));
        assertEquals("explicitBoxUser", c.load("c.Use").getMethod("run").invoke(null));
        Class<?> box = c.load("a.Box");
        String methods = java.util.Arrays.toString(box.getDeclaredMethods());
        assertEquals("b.BoxStr", box.getMethod("of", String.class).getReturnType().getName(), methods);
        assertTrue(methods.contains("b.BoxUser a.Box.of(b.User)"), methods);
        assertFalse(java.util.Arrays.stream(box.getMethods()).anyMatch(m -> m.getName().equals("twice") && m.getParameterTypes()[0] == String.class),
                "no bridge when the explicit class does not declare the factory");
    }

    @Test
    void userDeclaredOverloadIsNotBridgedTwiceAndDefaultPackageWorks() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("Box", """
                import dev.specialize.Specialize;
                @Specialize(types = int.class)
                public class Box<T> {
                    T v;
                    public static <T> Box<T> of(T v) { return new Box<>(); }
                    public static BoxInt of(int v) { BoxInt b = new BoxInt(); b.v = v + 1; return b; }
                }
                """, "Use", """
                public class Use { public static int run() { Box<int> b = Box.of(1); return b.v; } }
                """));
        assertTrue(c.success, c.allDiagnostics());
        // usages go to the specialization's own of(int); the hand-written overload stays in Box, exactly once
        assertEquals(0, c.load("Use").getMethod("run").invoke(null));
        assertEquals(1, java.util.Arrays.stream(c.load("Box").getDeclaredMethods())
                .filter(m -> m.getName().equals("of") && m.getParameterTypes()[0] == int.class).count());
        assertEquals(1, java.util.Arrays.stream(c.load("BoxInt").getDeclaredMethods()).filter(m -> m.getName().equals("of")).count());
    }
}
