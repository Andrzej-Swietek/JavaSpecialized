package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Compiles the real examples through the processor in this JVM, then client code against the resulting class files. */
class ExamplesCompileTest {

    @Test
    void examplesThenClientAgainstClassFiles() throws Exception {
        CompileHarness lib = CompileHarness.compile(CompileHarness.examplesSources());
        assertTrue(lib.success, lib.allDiagnostics());
        assertTrue(lib.generated("dev.specialize.examples.OptInt"));
        assertTrue(lib.generated("dev.specialize.examples.OptCodecBoolean"));
        assertFalse(lib.generated("dev.specialize.examples.OptUser"), "explicit specialization is not generated");

        CompileHarness client = CompileHarness.compile(Map.of("client.Client", """
                package client;

                import dev.specialize.Boxed;
                import dev.specialize.examples.*;
                import java.util.*;
                import java.util.function.Supplier;
                import static dev.specialize.examples.MathX.sq;
                import static dev.specialize.examples.MathX.*;

                public class Client {
                    Opt<int> field = Opt.empty();
                    Opt<int>[] array = new Opt[2];
                    @Boxed Opt<Integer> boxedField = Opt.empty();
                    @Boxed String plainBoxed = "x";
                    @Boxed Opt<Integer> boxedByDefault = Opt.fromOptional(java.util.Optional.of(1));
                    Opt<int> viaThis;

                    Client() { this.viaThis = Opt.<int>empty(); }

                    public static Opt<long> conditional(boolean b) { return b ? Opt.some(1L) : (Opt.empty()); }
                    public static Opt<double> cast(Object o) { return (Opt<double>) o; }
                    public static Opt<int> assign() { Opt<int> x; x = Opt.empty(); x = (Opt.some(1)); return x; }
                    public static Runnable lambda() { return () -> { Opt<int> l = Opt.empty(); l.isDefined(); }; }
                    public static Opt<int> supplierInLambda() {
                        Supplier<Object> s = () -> { return null; };
                        Supplier<Opt<int>> t = () -> Opt.<int>empty();
                        return s == null ? Opt.empty() : t.get();
                    }
                    public static String users() {
                        Opt<User> u = Opt.of(new User("a", 1));
                        OptUser adult = Opt.some(new User("b", 20));
                        return u.isDefined() + "/" + adult.isAdult() + "/" + Opt.<User>empty().nameOrAnonymous();
                    }
                    @Boxed public static Opt<Integer> boxedReturn() { return Opt.empty(); }
                    public static Object nestedBoxed() { List<@Boxed Opt<Integer>> l = List.of(Opt.<int>some(1).map(x -> x)); return l.get(0); }
                    public static int inline(int v) { return MathX.sq(3) + sumOfSquares(1, 2) + clamp(v, 0, 1) + sq(v) + MathX.<Integer>orDefault(1, 2); }
                    public static void voidInline() { MathX.log("x"); }
                    public static int notSimpleTwice(int[] a) { return MathX.between(a[0], 0, 9) ? 1 : 0; }
                    public static int simpleForms(Client c, int x) { return MathX.sq(-x) + MathX.sq((int) x) + MathX.sq(c.viaThis.get()) + MathX.sq(x) + MathX.sq(3); }
                    public static Opt<String> str() { return Opt.some("x"); }
                    public static Opt<boolean> flag() { return Opt.some(true).filter(b -> b); }
                    public static Opt<int> viaNested() { return Inner.make(); }
                    public static class Inner {
                        static Opt<int> make() { return Opt.empty(); }
                        Opt<int> f = Opt.empty();
                        Inner() { }
                        Inner(Opt<int> f) { this.f = f; }
                    }
                    public static Opt<int> viaOwnMethod() { return takes(Opt.empty(), 1); }
                    public static Opt<int> takes(Opt<int> o, int x) { return o; }
                    public static Opt<int> viaQualified() { return Client.takes(Opt.empty(), 1); }
                    public static Opt<int> viaNestedCtor() { return new Inner(Opt.empty()).f; }
                    public record Msg(Opt<int> a, Opt<long> b, String c) {}
                    public static Msg msg() { return new Msg(Opt.empty(), Opt.empty(), "c"); }
                    public static Object generic() { return Collections.<String>emptyList(); }
                    public static Map<String, Opt<int>> map() { return Map.of("a", Opt.<int>empty()); }
                    public static int receiverIsVariable(Opt<int> o) { return o.isDefined() ? o.get() : 0; }
                    public static String chain() { return List.of("a").get(0).toString(); }
                    public static Opt<int> viaClasspathCtor() { return new OptHolder(Opt.empty()).value(); }
                    public static <U> Opt<int> withU(U u) { return Opt.empty(); }
                    public static Opt<int> explicitOnOther() { return Client.<String>withU("x"); }
                    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Ann {}
                    List<@Ann Opt<int>> annotated = new ArrayList<>();
                    List<@Ann String> annotatedPlain = new ArrayList<>();
                    List<@Boxed String> boxedPlain = new ArrayList<>();
                    @Boxed Opt<Integer>[] boxedArray = new Opt[0];
                    static class Gen<X> { }
                    Gen<Integer> gen = new Gen<>();
                    public static Object local() { class Local { } return new Local(); }
                    public static Client.Inner innerRef() { return new Client.Inner(); }
                    public Client self() { return this; }
                    public static int fieldChain(Client c) { return c.self().viaThis.get(); }
                    static void takesGen(Gen<Integer> g, int x) { }
                    static void takesList(List<Integer> l) { }
                    static void takesGeneric(Opt<List<String>> o) { }
                    static Opt<int> var(Opt<int>... o) { return o[0]; }
                    public static Opt<int> params() { takesGen(null, 1); takesList(List.of()); takesGeneric(Opt.empty()); return var(Opt.<int>empty()); }
                }
                """, "client.Two", """
                package client;
                public class Two { }
                class Second { }
                """, "client.OptHolder", """
                package client;
                import dev.specialize.examples.Opt;
                public record OptHolder(Opt<int> value) {
                    OptHolder(Opt<int> value, int ignored) { this(value); }
                }
                """), List.of(lib.classes), List.of());
        assertTrue(client.success, client.allDiagnostics());

        ClassLoader loader = new java.net.URLClassLoader(
                new java.net.URL[]{client.classes.toUri().toURL(), lib.classes.toUri().toURL()}, getClass().getClassLoader());
        Class<?> c = Class.forName("client.Client", true, loader);
        String optInt = "dev.specialize.examples.OptInt";
        assertEquals(optInt, c.getDeclaredField("field").getType().getName());
        assertEquals(optInt + "[]", c.getDeclaredField("array").getType().getTypeName());
        assertEquals("dev.specialize.examples.Opt", c.getDeclaredField("boxedField").getType().getName());
        assertEquals("dev.specialize.examples.OptLong", c.getMethod("conditional", boolean.class).getReturnType().getName());
        assertEquals("dev.specialize.examples.Opt", c.getMethod("boxedReturn").getReturnType().getName());
        assertEquals("dev.specialize.examples.Opt", c.getMethod("nestedBoxed").invoke(null).getClass().getName());
        assertEquals("dev.specialize.examples.Opt", c.getDeclaredField("boxedByDefault").getType().getName());
        assertEquals(optInt, c.getMethod("assign").invoke(null).getClass().getName());
        assertEquals(optInt, c.getMethod("supplierInLambda").invoke(null).getClass().getName());
        assertEquals("true/true/anonymous", c.getMethod("users").invoke(null));
        assertEquals(9 + 5 + 1 + 4 + 1, c.getMethod("inline", int.class).invoke(null, 2));
        assertEquals(optInt, c.getMethod("viaNested").invoke(null).getClass().getName());
        assertEquals(optInt, c.getMethod("viaOwnMethod").invoke(null).getClass().getName());
        assertEquals(optInt, c.getMethod("viaQualified").invoke(null).getClass().getName());
        assertEquals(optInt, c.getMethod("viaNestedCtor").invoke(null).getClass().getName());
        assertEquals(optInt, c.getMethod("viaClasspathCtor").invoke(null).getClass().getName());
        Object msg = c.getMethod("msg").invoke(null);
        RecordComponent[] components = msg.getClass().getRecordComponents();
        assertEquals(optInt, components[0].getType().getName());
        assertEquals("dev.specialize.examples.OptLong", components[1].getType().getName());
        Method inline = c.getMethod("inline", int.class);
        assertEquals(int.class, inline.getReturnType());
        c.getMethod("voidInline").invoke(null);
        assertEquals(1, c.getMethod("notSimpleTwice", int[].class).invoke(null, (Object) new int[]{5}));
        assertEquals(optInt, c.getMethod("explicitOnOther").invoke(null).getClass().getName());
        assertEquals(optInt, c.getMethod("params").invoke(null).getClass().getName());
        assertEquals("dev.specialize.examples.Opt[]", c.getDeclaredField("boxedArray").getType().getTypeName());
    }

    @Test
    void librariesCompiledWithoutTheProcessorKeepTheGenericClass() throws Exception {
        CompileHarness lib = CompileHarness.compile(CompileHarness.examplesSources());
        assertTrue(lib.success, lib.allDiagnostics());
        CompileHarness plain = CompileHarness.compileUnprocessed(Map.of("plain.Plain", """
                package plain;
                import dev.specialize.examples.Opt;
                public class Plain {
                    public static Opt<Integer> take(Opt<Integer> generic, java.util.Map<String, String> m, int x, String s) { return generic; }
                    public static int prim(int x) { return x; }
                }
                """), List.of(lib.classes));
        assertTrue(plain.success, plain.allDiagnostics());
        CompileHarness client = CompileHarness.compile(Map.of("client.C", """
                package client;
                import dev.specialize.examples.Opt;
                import plain.Plain;
                public class C {
                    public static Object run() { return Plain.take(Opt.empty(), java.util.Map.of(), Plain.prim(1), "s"); }
                }
                """), List.of(lib.classes, plain.classes), List.of());
        assertTrue(client.success, client.allDiagnostics());
        assertEquals("dev.specialize.examples.Opt",
                client.load("client.C", lib.classes, plain.classes).getMethod("run").invoke(null).getClass().getName());
    }
}
