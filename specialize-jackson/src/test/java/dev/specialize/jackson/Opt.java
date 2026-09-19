package dev.specialize.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.specialize.Absent;
import dev.specialize.Prim;
import dev.specialize.Specialize;

/** A template with its JSON shape declared once: the specializations inherit the Jackson annotations. */
@Specialize(types = {int.class, long.class, String.class})
public final class Opt<T> {
    private final T value;
    private final boolean defined;

    private Opt(T value, boolean defined) {
        this.value = value;
        this.defined = defined;
    }

    @JsonCreator
    public static <T> Opt<T> some(T value) {
        return new Opt<>(value, true);
    }

    @Absent
    public static <T> Opt<T> empty() {
        return new Opt<>(Prim.zero(), false);
    }

    public boolean isDefined() {
        return defined;
    }

    public T get() {
        return value;
    }

    /** The JSON value: the payload itself, or {@code null}; boxing here happens only at the JSON boundary. */
    @JsonValue
    public Object toJson() {
        return defined ? value : null;
    }

    @Override
    public String toString() {
        return defined ? "Opt(" + Prim.str(value) + ")" : "Opt.Empty";
    }
}
