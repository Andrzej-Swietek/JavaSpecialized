package dev.specialize.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pattern matching over specialized classes: they are ordinary final classes, so every Java 21+ pattern applies. */
class PatternMatchingTest {

    /** {@code Opt<int>} in a type pattern is {@code OptInt}, exactly like in a declaration. */
    static String describe(Object o) {
        return switch (o) {
            case Opt<int> v when v.isDefined() -> "int " + (v.get() * 2);        // int get(), no unboxing
            case OptInt v -> "no int";                                          // the same class, spelled directly
            case Opt<String> s when s.isDefined() -> "string " + s.get().toUpperCase();
            case OptString s -> "no string";
            case OptUser u -> "user " + u.get().name();                          // the hand-written explicit specialization
            case Opt<?> other -> "generic " + other;                            // the generic class: types outside `types` (Opt<Character>)
            default -> "other";
        };
    }

    /** A record pattern deconstructs a record whose components are specializations. */
    static String summary(Object o) {
        if (o instanceof Demo.Payload(int id, String data, Opt<int> value, Opt<String> optional)) {
            int total = id + value.getOrElse(() -> 0);                          // OptInt.getOrElse(IntSupplier)
            return data + ":" + total + ":" + optional.getOrElse(() -> "-");
        }
        return "?";
    }

    static int nested(Object o) {
        return switch (o) {
            case Demo.Payload(var id, var data, Opt<int> value, var optional) when value.isDefined() -> value.get();
            case Demo.Payload p -> -p.id();
            default -> 0;
        };
    }

    @Test
    void typePatternsAndRecordPatterns() {
        assertEquals("int 10", describe(Opt.some(5)));
        assertEquals("no int", describe(Opt.<int>empty()));
        assertEquals("string X", describe(Opt.some("x")));
        assertEquals("no string", describe(Opt.<String>empty()));
        assertEquals("user Ala", describe(Opt.some(new User("Ala", 30))));
        assertEquals("generic Opt(c)", describe(Opt.some('c')));
        assertEquals("other", describe("plain"));

        Demo.Payload payload = new Demo.Payload(1, "d", Opt.some(41), Opt.empty());
        assertEquals("d:42:-", summary(payload));
        assertEquals("?", summary(List.of()));
        assertEquals(41, nested(payload));
        assertEquals(-2, nested(new Demo.Payload(2, "e", Opt.empty(), Opt.some("o"))));
        assertEquals(0, nested("x"));
        assertEquals(OptInt.class, payload.value().getClass());
        assertEquals(OptString.class, payload.optional().getClass());
    }
}
