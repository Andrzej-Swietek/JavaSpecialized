package demo.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.specialize.Absent;
import dev.specialize.Prim;
import dev.specialize.Specialize;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The application's own optional, specialized for the primitives it stores in messages. */
@Specialize(types = {int.class, long.class, double.class})
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

    /** JSON: the payload or {@code null}; missing fields come back as {@link #empty()} thanks to SpecializeModule. */
    @JsonValue
    public Object toJson() {
        return defined ? value : null;
    }

    public boolean isDefined() {
        return defined;
    }

    public T get() {
        if (!defined) {
            throw new IllegalStateException("empty");
        }
        return value;
    }

    public T getOrElse(Supplier<T> other) {
        return defined ? value : other.get();
    }

    public Opt<T> filter(Predicate<T> p) {
        return defined && p.test(value) ? this : empty();
    }

    public <R> R fold(R ifEmpty, Function<T, R> f) {
        return defined ? f.apply(value) : ifEmpty;
    }

    @Override
    public String toString() {
        return defined ? "Opt(" + Prim.str(value) + ")" : "Opt.Empty";
    }
}
