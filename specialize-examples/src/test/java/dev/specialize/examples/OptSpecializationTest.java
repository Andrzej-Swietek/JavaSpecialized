package dev.specialize.examples;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.specialize.Boxed;
import dev.specialize.Specialized;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * These tests are compiled with the processor on the test annotation processor path, against the already compiled
 * main classes. So they exercise the class-file path: templates, bridges and explicit specializations are
 * discovered from {@code Opt.class} / {@code OptUser.class}, not from source.
 */
class OptSpecializationTest {

    @Test
    void generatedClassUsesRealPrimitives() throws Exception {
        assertEquals(int.class, OptInt.class.getMethod("get").getReturnType());
        assertEquals(int.class, OptInt.class.getDeclaredField("value").getType());
        assertEquals(int.class, OptInt.class.getMethod("some", int.class).getParameterTypes()[0]);
        assertEquals(int[].class, OptInt.class.getMethod("toArray").getReturnType());
        assertEquals(long.class, OptLong.class.getMethod("get").getReturnType());
        assertEquals(double.class, OptDouble.class.getMethod("get").getReturnType());
        assertEquals(boolean.class, OptBoolean.class.getMethod("get").getReturnType());
        assertEquals(String.class, OptString.class.getMethod("get").getReturnType());
        // there is no boxed overload anywhere
        for (Method m : OptInt.class.getMethods()) {
            for (Class<?> p : m.getParameterTypes()) {
                assertNotEquals(Integer.class, p, m.toString());
            }
        }
    }

    @Test
    void functionalInterfacesAreSpecialized() throws Exception {
        assertEquals(IntPredicate.class, OptInt.class.getMethod("filter", IntPredicate.class).getParameterTypes()[0]);
        assertEquals(IntUnaryOperator.class, OptInt.class.getMethod("transform", IntUnaryOperator.class).getParameterTypes()[0]);
        assertEquals(IntSupplier.class, OptInt.class.getMethod("getOrElse", IntSupplier.class).getParameterTypes()[0]);
        assertEquals(OptInt.class, OptInt.class.getMethod("transform", IntUnaryOperator.class).getReturnType());
    }

    @Test
    void generatedClassIsMarked() {
        Specialized marker = OptInt.class.getAnnotation(Specialized.class);
        assertEquals(Opt.class, marker.of());
        assertEquals(int.class, marker.type());
        assertTrue(marker.generated());
        assertTrue(Modifier.isFinal(OptInt.class.getModifiers()));
    }

    @Test
    void primHelpersBecomeConstants() {
        assertEquals(int.class, OptInt.valueType());
        assertEquals(Object.class, Opt.valueType());
        assertTrue(OptInt.isPrimitiveSpecialization());
        assertFalse(Opt.isPrimitiveSpecialization());
        assertEquals(0, OptInt.empty().orNull());
        assertEquals(0L, OptLong.empty().orNull());
        assertEquals(false, OptBoolean.empty().orNull());
        assertEquals(Integer.hashCode(42), OptInt.some(42).hashCode());
        assertEquals(OptDouble.some(Double.NaN), OptDouble.some(Double.NaN), "Double.compare semantics like Double.equals");
    }

    @Test
    void behaviourMatchesGenericOpt() {
        OptInt some = OptInt.some(5);
        OptInt none = OptInt.empty();
        assertTrue(some.isDefined());
        assertTrue(none.isEmpty());
        assertEquals(5, some.get());
        assertThrows(NoSuchElementException.class, none::get);
        assertEquals(5, some.getOrElse(1));
        assertEquals(1, none.getOrElse(1));
        assertEquals(7, none.getOrElse(() -> 7));
        assertTrue(some.contains(5));
        assertFalse(some.contains(6));
        assertTrue(some.exists(x -> x > 4));
        assertTrue(none.forall(x -> false));
        assertEquals(OptInt.some(50), some.transform(x -> x * 10));
        assertSame(none, none.transform(x -> x * 10));
        assertEquals("5", some.map(String::valueOf).get()); // map() returns the generic Opt<R>
        assertEquals(OptInt.some(5), some.filter(x -> x > 4));
        assertEquals(OptInt.empty(), some.filterNot(x -> x > 4));
        assertEquals(some, none.orElse(some));
        assertEquals(some, none.orElse(() -> some));
        assertEquals("five", some.fold(() -> "none", x -> "five"));
        assertArrayEquals(new int[]{5}, some.toArray());
        assertArrayEquals(new int[0], none.toArray());
        assertEquals(List.of(5), some.toList());
        assertEquals(java.util.Optional.of(5), some.toOptional());
        assertEquals(OptInt.some(5), OptInt.fromOptional(java.util.Optional.of(5)));
        assertEquals("Opt(5)", some.toString());
        assertEquals("Opt.Empty", none.toString());
        int total = 0;
        for (int v : some) {
            total += v;
        }
        assertEquals(5, total);
    }

    @Test
    void clientTypeArgumentsAreRewrittenToSpecializations() {
        Opt<int> primitiveSyntax = Opt.<int>some(7);
        Opt<int> viaBridge = Opt.some(5);
        Opt<long> longs = Opt.some(3L);
        Opt<double> doubles = Opt.of(2.5);
        Opt<boolean> flags = Opt.some(true);
        Opt<String> strings = Opt.some("s");
        Opt<int> emptyViaVarType = Opt.empty();
        Opt<int> viaNew = Opt.<int>empty();

        assertSame(OptInt.class, ((Object) viaBridge).getClass());
        assertSame(OptInt.class, ((Object) primitiveSyntax).getClass());
        assertSame(OptLong.class, ((Object) longs).getClass());
        assertSame(OptDouble.class, ((Object) doubles).getClass());
        assertSame(OptBoolean.class, ((Object) flags).getClass());
        assertSame(OptString.class, ((Object) strings).getClass());
        assertSame(OptInt.class, ((Object) emptyViaVarType).getClass());
        assertSame(OptInt.class, ((Object) viaNew).getClass());
        int unboxedFree = viaBridge.get() + primitiveSyntax.get();
        assertEquals(12, unboxedFree);

        Opt<List<String>> generic = Opt.some(List.of("a"));
        assertSame(Opt.class, ((Object) generic).getClass());
        // a String argument always selects the String bridge (plain Java overload rules); cast to opt out
        Opt<Object> objects = Opt.some((Object) "boxed on purpose");
        assertSame(Opt.class, ((Object) objects).getClass());
    }

    @Test
    void boxedSpellingIsIdeFriendlyAndStillSpecialized() {
        Opt<Integer> boxed = Opt.some(5);            // type-checks as plain generics in any IDE, compiles to OptInt
        @Boxed Opt<Integer> generic = Opt.fromOptional(java.util.Optional.of(5));
        assertSame(OptInt.class, ((Object) boxed).getClass());
        assertSame(Opt.class, ((Object) generic).getClass());
        assertEquals(5, boxed.get() + generic.get() - 5);
    }

    static Opt<int> firstPositive(int... values) { // primitive syntax in a signature: the javac plugin makes it legal
        for (int v : values) {
            if (v > 0) {
                return Opt.some(v);
            }
        }
        return Opt.empty();
    }

    @Test
    void returnTypesFieldsAndNestedGenericsAreRewritten() throws Exception {
        assertEquals(OptInt.class, getClass().getDeclaredMethod("firstPositive", int[].class).getReturnType());
        assertEquals(OptInt.some(3), firstPositive(-1, 3));
        assertEquals(OptInt.empty(), firstPositive());

        List<Opt<int>> list = List.of(Opt.some(1), Opt.<int>empty(), OptInt.some(2), Opt.of(3)); // Opt.of(int) bridge
        assertEquals(List.of(OptInt.some(1), OptInt.empty(), OptInt.some(2), OptInt.some(3)), list);
        assertEquals(List.of(OptUser.of(null)), List.of(Opt.of((User) null))); // Opt.of(User) bridge → explicit class
        Opt<int>[] array = new Opt[0];
        assertSame(OptInt[].class, array.getClass());
    }

    @Test
    void bridgesInGenericClassPickThePrimitiveOverload() {
        String javap = Bytecode.of(OptSpecializationTest.class);
        String body = Bytecode.method(javap, "void clientTypeArgumentsAreRewrittenToSpecializations()");
        assertTrue(body.contains("OptInt.some:(I)") || body.contains("Opt.some:(I)"), body);
        assertTrue(body.contains("OptLong.some:(J)") || body.contains("Opt.some:(J)"), body);
        assertTrue(body.contains("OptInt.get:()I"), "no unboxing: " + body);
        assertFalse(body.contains("Integer.valueOf"), "boxing found: " + body);
        assertFalse(body.contains("Integer.intValue"), "unboxing found: " + body);
    }

    @Test
    void explicitSpecializationWinsOverTemplate() throws Exception {
        Opt<User> user = Opt.some(new User("Ala", 30));
        Opt<User> nobody = Opt.empty();
        assertSame(OptUser.class, ((Object) user).getClass());
        assertSame(OptUser.class, ((Object) nobody).getClass());
        assertEquals("Ala", user.get().name());

        OptUser explicit = user;
        assertEquals("Ala", explicit.nameOrAnonymous());
        assertTrue(explicit.isAdult());
        assertEquals("anonymous", OptUser.empty().nameOrAnonymous());

        // Opt.some(User) is bridged because OptUser declares some(User); no OptUser class was generated
        assertEquals(OptUser.class, Opt.class.getMethod("some", User.class).getReturnType());
        assertEquals(OptUser.class, Opt.class.getMethod("of", User.class).getReturnType());
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.specialize.examples.OptUserGenerated"));
        Specialized marker = OptUser.class.getAnnotation(Specialized.class);
        assertFalse(marker.generated());
    }
}
