package dev.specialize.examples;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The generic template itself (used for every T that is not specialized), the explicit OptUser, MathX and Demo. */
class GenericOptTest {

    @Test
    void genericOptBehaviour() {
        Opt<List<String>> some = Opt.some(List.of("a"));
        Opt<List<String>> none = Opt.empty();
        assertSame(Opt.class, ((Object) some).getClass());
        assertTrue(some.isDefined());
        assertTrue(none.isEmpty());
        assertEquals(List.of("a"), some.get());
        assertThrows(NoSuchElementException.class, none::get);
        assertEquals(List.of("a"), some.getOrElse(List.of()));
        assertEquals(List.of(), none.getOrElse(List.of()));
        assertEquals(List.of("a"), some.getOrElse(() -> List.of("b")));
        assertEquals(List.of("b"), none.getOrElse(() -> List.of("b")));
        assertFalse(some.isEmpty());
        assertNull(none.orNull());
        assertEquals(List.of("a"), some.orNull());
        assertTrue(some.contains(List.of("a")));
        assertFalse(some.contains(List.of("b")));
        assertFalse(none.contains(List.of("a")));
        assertTrue(some.exists(l -> l.size() == 1));
        assertFalse(some.exists(List::isEmpty));
        assertFalse(none.exists(l -> true));
        assertTrue(none.forall(l -> false));
        assertTrue(some.forall(l -> true));
        assertFalse(some.forall(List::isEmpty));
        assertEquals(1, some.map(List::size).get());
        assertTrue(none.map(List::size).isEmpty());
        assertEquals(Opt.some(List.of("a", "a")), some.transform(l -> List.of("a", "a")));
        assertSame(none, none.transform(l -> l));
        assertEquals(Opt.some(List.of("a", "b")), some.flatMap(l -> Opt.some(List.of("a", "b"))));
        assertEquals(Opt.<List<String>>empty(), none.flatMap(l -> Opt.some(List.of("a", "b"))));
        assertSame(some, some.filter(l -> !l.isEmpty()));
        assertTrue(some.filter(List::isEmpty).isEmpty());
        assertTrue(none.filter(l -> true).isEmpty());
        assertSame(some, some.filterNot(List::isEmpty));
        assertTrue(some.filterNot(l -> true).isEmpty());
        assertTrue(none.filterNot(l -> false).isEmpty());
        assertSame(some, some.orElse(none));
        assertSame(some, none.orElse(some));
        assertSame(some, some.orElse(() -> none));
        assertSame(some, none.orElse(() -> some));
        assertEquals(1, some.fold(() -> 0, List::size));
        assertEquals(0, none.fold(() -> 0, List::size));
        List<Object> seen = new java.util.ArrayList<>();
        some.foreach(seen::add);
        none.foreach(seen::add);
        assertEquals(List.of(List.of("a")), seen);
        assertEquals(Optional.of(List.of("a")), some.toOptional());
        assertEquals(Optional.empty(), none.toOptional());
        assertEquals(List.of(List.of("a")), some.toList());
        assertEquals(List.of(), none.toList());
        assertArrayEquals(new Object[]{List.of("a")}, some.toArray());
        assertArrayEquals(new Object[0], none.toArray());
        assertEquals(1, some.stream().count());
        assertEquals(0, none.stream().count());
        assertTrue(some.iterator().hasNext());
        assertFalse(none.iterator().hasNext());
        assertEquals(Object.class, Opt.valueType());
        assertFalse(Opt.isPrimitiveSpecialization());
        assertEquals(Opt.some(List.of("a")), some);
        assertNotEquals(some, Opt.some(List.of("b")));
        assertNotEquals(some, none);
        assertEquals(none, Opt.<List<String>>empty());
        assertNotEquals(some, "x");
        assertEquals(some, some);
        assertEquals(List.of("a").hashCode(), some.hashCode());
        assertEquals(0, none.hashCode());
        assertEquals("Opt([a])", some.toString());
        assertEquals("Opt.Empty", none.toString());
        assertEquals(Opt.<List<String>>empty(), Opt.of((List<String>) null));
        assertEquals(some, Opt.of(List.of("a")));
        assertEquals(some, Opt.fromOptional(Optional.of(List.of("a"))));
        assertEquals(none, Opt.fromOptional(Optional.<List<String>>empty()));
    }

    @Test
    void explicitOptUser() {
        User ala = new User("Ala", 30);
        OptUser some = OptUser.some(ala);
        OptUser none = OptUser.empty();
        assertThrows(NullPointerException.class, () -> OptUser.some(null));
        assertSame(none, OptUser.of(null));
        assertEquals(some, OptUser.of(ala));
        assertTrue(some.isDefined());
        assertFalse(none.isDefined());
        assertFalse(some.isEmpty());
        assertTrue(none.isEmpty());
        assertEquals(ala, some.get());
        assertThrows(NoSuchElementException.class, none::get);
        assertEquals(ala, some.getOrElse(null));
        assertEquals(ala, none.getOrElse(ala));
        assertEquals("Ala", some.nameOrAnonymous());
        assertEquals("anonymous", none.nameOrAnonymous());
        assertTrue(some.isAdult());
        assertFalse(OptUser.some(new User("kid", 5)).isAdult());
        assertFalse(none.isAdult());
        assertEquals(some, OptUser.some(ala));
        assertNotEquals(some, none);
        assertNotEquals(some, "x");
        assertEquals(ala.hashCode(), some.hashCode());
        assertEquals(0, none.hashCode());
        assertEquals("Opt(User[name=Ala, age=30])", some.toString());
        assertEquals("Opt.Empty", none.toString());
    }

    @Test
    void inlineMethodsRemainOrdinaryMethodsForReflectionAndOtherCompilers() throws Exception {
        assertEquals(9, MathX.class.getMethod("sq", int.class).invoke(null, 3));
        assertEquals(4096L, MathX.class.getMethod("kb", long.class).invoke(null, 4L));
        assertEquals(2.5, MathX.class.getMethod("half", double.class).invoke(null, 5.0));
        assertEquals(5.0, MathX.class.getMethod("lerp", double.class, double.class, double.class).invoke(null, 0.0, 10.0, 0.5));
        assertEquals(10, MathX.class.getMethod("clamp", int.class, int.class, int.class).invoke(null, 15, 0, 10));
        assertEquals(25, MathX.class.getMethod("sumOfSquares", int.class, int.class).invoke(null, 3, 4));
        assertEquals(true, MathX.class.getMethod("between", int.class, int.class, int.class).invoke(null, 5, 0, 10));
        assertEquals(false, MathX.class.getMethod("between", int.class, int.class, int.class).invoke(null, -1, 0, 10));
        assertEquals(false, MathX.class.getMethod("between", int.class, int.class, int.class).invoke(null, 11, 0, 10));
        assertEquals("f", MathX.class.getMethod("orDefault", Object.class, Object.class).invoke(null, null, "f"));
        assertEquals("v", MathX.class.getMethod("orDefault", Object.class, Object.class).invoke(null, "v", "f"));
        assertEquals(6, MathX.class.getMethod("normal", int.class).invoke(null, 2));
        MathX.class.getMethod("trace", String.class, int.class).invoke(null, "t", 1);
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            MathX.class.getMethod("log", String.class).invoke(null, "hi");
        } finally {
            System.setOut(original);
        }
        assertEquals("[MathX] hi", captured.toString().trim());
    }

    @Test
    void genericCodecFallsBackToObjectStreams() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            OptCodec.write(out, Opt.some(List.of("a")));
            OptCodec.write(out, Opt.<List<String>>empty());
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(Opt.some(List.of("a")), OptCodec.<List<String>>read(in));
            assertEquals(Opt.<List<String>>empty(), OptCodec.<List<String>>read(in));
        }
        assertEquals(0, OptCodec.maxSize());
        ByteBuffer buffer = ByteBuffer.allocate(512);
        OptCodec.put(buffer, Opt.<List<String>>empty());
        OptCodec.put(buffer, Opt.some(List.of("serialized")));
        buffer.flip();
        assertEquals(Opt.<List<String>>empty(), OptCodec.<List<String>>get(buffer));
        assertEquals(Opt.some(List.of("serialized")), OptCodec.<List<String>>get(buffer));
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        OptCodec.write(new java.io.DataOutputStream(plain), Opt.some(List.of("data")));
        assertEquals(Opt.some(List.of("data")), OptCodec.<List<String>>read(new java.io.DataInputStream(new ByteArrayInputStream(plain.toByteArray()))));
    }

    @Test
    void demoRuns() {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            Demo.main(new String[0]);
        } finally {
            System.setOut(original);
        }
        String out = captured.toString();
        assertTrue(out.contains("a.getClass() = OptInt"), out);
        assertTrue(out.contains("firstEven = Opt(8) / Opt.Empty"), out);
        assertTrue(out.contains("sq(3) = 9, kb(4) = 4096"), out);
    }
}
