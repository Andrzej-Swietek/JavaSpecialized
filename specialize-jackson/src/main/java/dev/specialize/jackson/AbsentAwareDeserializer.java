package dev.specialize.jackson;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import java.util.function.Supplier;

/** The template's own deserializer, with {@code null} and "field missing" mapped to the {@code @Absent} value. */
final class AbsentAwareDeserializer extends DelegatingDeserializer {
    private final Supplier<Object> absent;

    AbsentAwareDeserializer(JsonDeserializer<?> delegate, Supplier<Object> absent) {
        super(delegate);
        this.absent = absent;
    }

    @Override
    protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
        return new AbsentAwareDeserializer(newDelegatee, absent);
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        return absent.get();
    }

    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return absent.get();
    }
}
