package dev.specialize.examples;

import java.util.Objects;

import dev.specialize.Specialized;
import java.util.NoSuchElementException;

/**
 * Explicit specialization of {@code Opt<T>} for {@link User}, like an explicit template specialization in C++.
 * It wins over the template: nothing is generated for {@code Opt<User>}, every {@code Opt<User>} in client code
 * becomes {@code OptUser}, and {@code Opt.some(user)} is bridged to {@link #some(User)} because that factory exists here.
 *
 * <p>The representation is deliberately different from the template: {@code null} is the empty marker, so an
 * {@code OptUser} is a single reference.
 */
@Specialized(of = Opt.class, type = User.class)
public final class OptUser {
    private static final OptUser EMPTY = new OptUser(null);

    private final User value;

    private OptUser(User value) {
        this.value = value;
    }

    public static OptUser some(User value) {
        if (value == null) {
            throw new NullPointerException("OptUser.some(null)");
        }
        return new OptUser(value);
    }

    public static OptUser of(User nullable) {
        return nullable == null ? EMPTY : new OptUser(nullable);
    }

    public static OptUser empty() {
        return EMPTY;
    }

    public boolean isDefined() {
        return value != null;
    }

    public boolean isEmpty() {
        return value == null;
    }

    public User get() {
        if (value == null) {
            throw new NoSuchElementException("OptUser.Empty.get");
        }
        return value;
    }

    public User getOrElse(User other) {
        return value != null ? value : other;
    }

    /** Something the template cannot offer: domain-specific behaviour for this one T. */
    public String nameOrAnonymous() {
        return value != null ? value.name() : "anonymous";
    }

    public boolean isAdult() {
        return value != null && value.age() >= 18;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OptUser that && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value != null ? "Opt(" + value + ")" : "Opt.Empty";
    }
}
