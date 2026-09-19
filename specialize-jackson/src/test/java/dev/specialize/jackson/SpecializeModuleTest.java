package dev.specialize.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.specialize.Absent;
import dev.specialize.Specialized;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpecializeModuleTest {
    record Point(int x, int y) {
    }

    /** A Kafka-style message: primitive-backed optionals, a string one and a generic one. */
    record OrderEvent(long id, Opt<int> qty, Opt<long> ts, Opt<String> note, Opt<Point> where) {
    }

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new SpecializeModule());

    @Test
    void specializationsSerializeAsTheirPayload() throws Exception {
        OrderEvent event = new OrderEvent(1, Opt.some(3), Opt.empty(), Opt.some("x"), Opt.some(new Point(1, 2)));
        assertEquals(OptInt.class, event.qty().getClass());
        assertEquals("""
                {"id":1,"qty":3,"ts":null,"note":"x","where":{"x":1,"y":2}}""", mapper.writeValueAsString(event));
    }

    @Test
    void nullAndMissingBecomeTheAbsentValue() throws Exception {
        OrderEvent event = mapper.readValue("""
                {"id":7,"qty":null,"note":"n","where":{"x":5,"y":6}}""", OrderEvent.class);
        assertFalse(event.qty().isDefined(), "null → empty, not a null reference");
        assertFalse(event.ts().isDefined(), "missing → empty");
        assertEquals(OptInt.class, event.qty().getClass());
        assertEquals(OptLong.class, event.ts().getClass());
        assertEquals("n", event.note().get());
        assertEquals(new Point(5, 6), event.where().get());
        OrderEvent full = mapper.readValue("""
                {"id":7,"qty":42,"ts":9,"note":null}""", OrderEvent.class);
        assertEquals(42, full.qty().get());
        assertEquals(9L, full.ts().get());
        assertFalse(full.note().isDefined());
        assertFalse(full.where().isDefined());
        assertEquals("[Opt(1), Opt.Empty]", mapper.readValue("[1, null]", mapper.getTypeFactory().constructCollectionType(List.class, OptInt.class)).toString());
    }

    @Test
    void moduleIsDiscoverable() throws Exception {
        ObjectMapper discovered = new ObjectMapper().findAndRegisterModules();
        assertTrue(discovered.getRegisteredModuleIds().contains("specialize"));
        assertFalse(discovered.readValue("{\"id\":1}", OrderEvent.class).qty().isDefined());
    }

    @Specialized(of = Opt.class, type = Point.class)
    static final class OptPoint {
        static int calls;

        @Absent
        static OptPoint empty() {
            calls++;
            throw new UnsupportedOperationException("no");
        }

        @Absent
        OptPoint notStatic() {
            return this;
        }

        @Absent
        static OptPoint notNoArg(int x) {
            return null;
        }
    }

    @Specialized(of = Opt.class, type = Object.class)
    record NoAbsent(String v) {
    }

    @Specialized(of = Opt.class, type = Void.class)
    static final class TwoAbsent {
        @Absent
        static TwoAbsent empty() {
            return null;
        }

        @Absent
        static TwoAbsent none() {
            return null;
        }
    }

    @Test
    void delegateReplacementKeepsTheAbsentValue() {
        com.fasterxml.jackson.databind.JsonDeserializer<?> plain = new com.fasterxml.jackson.databind.deser.std.StringDeserializer();
        AbsentAwareDeserializer aware = new AbsentAwareDeserializer(plain, () -> "absent");
        com.fasterxml.jackson.databind.JsonDeserializer<?> replaced = aware.replaceDelegatee(new com.fasterxml.jackson.databind.deser.std.StringDeserializer());
        assertTrue(replaced instanceof AbsentAwareDeserializer && replaced != aware);
        assertEquals("absent", ((AbsentAwareDeserializer) replaced).getNullValue(null));
        assertEquals("absent", ((AbsentAwareDeserializer) replaced).getAbsentValue(null));
    }

    @Test
    void severalAbsentFactoriesAreRejected() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> mapper.readValue("null", TwoAbsent.class));
        assertTrue(failure.getMessage().endsWith("TwoAbsent declares 2 no-argument @Absent factories (empty, none); keep one"), failure.getMessage());
    }

    @Test
    void classesWithoutTheAnnotationsKeepJacksonDefaults() throws Exception {
        assertEquals(null, mapper.readValue("null", Point.class));
        assertEquals(null, mapper.readValue("null", NoAbsent.class), "@Specialized without @Absent: Jackson's own null");
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> mapper.readValue("null", OptPoint.class));
        assertTrue(failure.getMessage().startsWith("@Absent factory"), failure.getMessage());
        assertEquals(1, OptPoint.calls);
    }
}
