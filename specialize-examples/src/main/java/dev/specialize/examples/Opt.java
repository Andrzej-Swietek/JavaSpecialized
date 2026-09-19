package dev.specialize.examples;

import dev.specialize.Prim;
import dev.specialize.Specialize;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Non-boxing option type modelled after {@code com.avsystem.commons.misc.Opt[A]} from AVSystem scala-commons.
 *
 * <p>This is the <em>template</em>. From it the processor generates {@code OptInt}, {@code OptLong}, {@code OptDouble},
 * {@code OptBoolean} and {@code OptString}, where {@code T} is a real {@code int} / {@code long} / … field and no boxing
 * happens anywhere. Client code keeps writing {@code Opt<Integer>} (or {@code Opt<int>}) and {@code Opt.some(5)};
 * the processor swaps in the specialized class at compile time.
 *
 * <p>The only concessions to the template mechanism are the {@link Prim} helpers, which replace {@code null},
 * {@code equals}, {@code hashCode}, {@code String.valueOf} and array creation on {@code T}, and the convention that
 * {@code static <T>} factories share the class type variable's name.
 */
@Specialize(types = {int.class, long.class, double.class, boolean.class, String.class})
public final class Opt<T> implements Iterable<T> {

    private static final Opt<?> EMPTY = new Opt<>(Prim.zero(), false);

    private final T value;
    private final boolean defined;

    private Opt(T value, boolean defined) {
        this.value = value;
        this.defined = defined;
    }


    /** Wraps a (possibly {@code null}) value. In primitive specializations {@code null} does not exist. */
    public static <T> Opt<T> some(T value) {
        return new Opt<>(value, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> Opt<T> empty() {
        return (Opt<T>) EMPTY;
    }

    /** Like {@code Opt(nullable)} in scala-commons: {@code null} becomes {@code Empty}. */
    public static <T> Opt<T> of(T nullable) {
        return Prim.isNull(nullable) ? empty() : some(nullable);
    }

    public static <T> Opt<T> fromOptional(Optional<T> optional) {
        return optional.isPresent() ? some(optional.get()) : empty();
    }


    public boolean isDefined() {
        return defined;
    }

    public boolean isEmpty() {
        return !defined;
    }

    public T get() {
        if (!defined) {
            throw new NoSuchElementException("Opt.Empty.get");
        }
        return value;
    }

    public T getOrElse(T other) {
        return defined ? value : other;
    }

    public T getOrElse(Supplier<T> other) {
        return defined ? value : other.get();
    }

    /** {@code null} for empty in the generic class, the primitive zero in specializations. */
    public T orNull() {
        return defined ? value : Prim.zero();
    }

    public boolean contains(T candidate) {
        return defined && Prim.eq(value, candidate);
    }

    public boolean exists(Predicate<T> p) {
        return defined && p.test(value);
    }

    public boolean forall(Predicate<T> p) {
        return !defined || p.test(value);
    }


    /** Result is the generic {@code Opt<R>}; use {@link #transform} to stay in the specialization. */
    public <R> Opt<R> map(Function<T, R> f) {
        return defined ? Opt.some(f.apply(value)) : Opt.empty();
    }

    /** {@code T → T} mapping that stays specialized: {@code OptInt.transform(IntUnaryOperator)} returns {@code OptInt}. */
    public Opt<T> transform(UnaryOperator<T> f) {
        return defined ? some(f.apply(value)) : this;
    }

    public <R> Opt<R> flatMap(Function<T, Opt<R>> f) {
        return defined ? f.apply(value) : Opt.empty();
    }

    public Opt<T> filter(Predicate<T> p) {
        return defined && p.test(value) ? this : empty();
    }

    public Opt<T> filterNot(Predicate<T> p) {
        return defined && !p.test(value) ? this : empty();
    }

    public Opt<T> orElse(Opt<T> other) {
        return defined ? this : other;
    }

    public Opt<T> orElse(Supplier<Opt<T>> other) {
        return defined ? this : other.get();
    }

    public <R> R fold(Supplier<R> ifEmpty, Function<T, R> ifDefined) {
        return defined ? ifDefined.apply(value) : ifEmpty.get();
    }

    public void foreach(Consumer<T> action) {
        if (defined) {
            action.accept(value);
        }
    }


    public Optional<T> toOptional() {
        return defined ? Optional.of(value) : Optional.empty();
    }

    public List<T> toList() {
        return defined ? List.of(value) : List.of();
    }

    /** {@code int[]} in {@code OptInt}: zero or one element. */
    public T[] toArray() {
        T[] array = Prim.newArray(defined ? 1 : 0);
        if (defined) {
            array[0] = value;
        }
        return array;
    }

    public Stream<T> stream() {
        return defined ? Stream.of(value) : Stream.empty();
    }

    @Override
    public Iterator<T> iterator() {
        return defined ? List.of(value).iterator() : Collections.emptyIterator();
    }

    /** {@code Object.class} here, {@code int.class} in {@code OptInt}: a compile-time constant. */
    public static Class<?> valueType() {
        return Prim.type();
    }

    public static boolean isPrimitiveSpecialization() {
        return Prim.isPrimitive();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Opt<?> that)) {
            return false;
        }
        return defined == that.defined && Prim.eq(value, that.value);
    }

    @Override
    public int hashCode() {
        return defined ? Prim.hash(value) : 0;
    }

    @Override
    public String toString() {
        return defined ? "Opt(" + Prim.str(value) + ")" : "Opt.Empty";
    }
}
