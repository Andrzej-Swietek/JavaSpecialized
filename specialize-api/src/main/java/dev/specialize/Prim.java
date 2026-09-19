package dev.specialize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Helpers for writing {@link Specialize} templates in a way that works for both the generic class and its
 * primitive specializations. In the generic class they are ordinary (boxing-free) runtime calls; in a primitive
 * specialization the processor replaces every call with the equivalent primitive expression, so there is no
 * runtime cost at all.
 *
 * <table>
 *   <caption>Rewrites in a specialization for {@code int}</caption>
 *   <tr><th>template</th><th>specialized</th></tr>
 *   <tr><td>{@code Prim.zero()}</td><td>{@code 0}</td></tr>
 *   <tr><td>{@code Prim.eq(a, b)}</td><td>{@code (a == b)} ({@code Double.compare(a, b) == 0} for floating point)</td></tr>
 *   <tr><td>{@code Prim.hash(a)}</td><td>{@code Integer.hashCode(a)}</td></tr>
 *   <tr><td>{@code Prim.str(a)}</td><td>{@code String.valueOf(a)}</td></tr>
 *   <tr><td>{@code Prim.compare(a, b)}</td><td>{@code Integer.compare(a, b)}</td></tr>
 *   <tr><td>{@code Prim.newArray(n)}</td><td>{@code new int[n]}</td></tr>
 *   <tr><td>{@code Prim.isNull(a)}</td><td>{@code false}</td></tr>
 *   <tr><td>{@code Prim.isPrimitive()}</td><td>{@code true}</td></tr>
 *   <tr><td>{@code Prim.type()}</td><td>{@code int.class}</td></tr>
 *   <tr><td>{@code Prim.box(a)}</td><td>{@code Integer.valueOf(a)}</td></tr>
 *   <tr><td>{@code Prim.write(out, a)} / {@code Prim.read(in)}</td><td>{@code out.writeInt(a)} / {@code in.readInt()}</td></tr>
 *   <tr><td>{@code Prim.put(buf, a)} / {@code Prim.get(buf)}</td><td>{@code buf.putInt(a)} / {@code buf.getInt()}</td></tr>
 *   <tr><td>{@code Prim.bytes()}</td><td>{@code Integer.BYTES}</td></tr>
 * </table>
 */
public final class Prim {
    /** Bounds the generic {@link #read} fallback; it does not restrict which classes may be deserialized. */
    private static final ObjectInputFilter LIMITS = ObjectInputFilter.Config.createFilter("maxdepth=64;maxrefs=100000;maxbytes=16777216;maxarray=1000000;*");

    private Prim() {
    }

    /** {@code null} in the generic class, the primitive zero value in a specialization. */
    public static <T> T zero() {
        return null;
    }

    public static <T> boolean eq(T a, T b) {
        return Objects.equals(a, b);
    }

    public static <T> int hash(T a) {
        return Objects.hashCode(a);
    }

    public static <T> String str(T a) {
        return String.valueOf(a);
    }

    /** {@code Integer.compare} in a specialization; the generic fallback requires {@code T} to be {@link Comparable}. */
    @SuppressWarnings("unchecked")
    public static <T> int compare(T a, T b) {
        return ((Comparable<T>) a).compareTo(b);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] newArray(int length) {
        return (T[]) new Object[length];
    }

    public static <T> boolean isNull(T a) {
        return a == null;
    }

    /** {@code false} here; a compile-time {@code true} constant in primitive specializations (dead branches are dropped by javac). */
    public static boolean isPrimitive() {
        return false;
    }

    /** {@code Object.class} here; the primitive class literal in specializations. */
    public static Class<?> type() {
        return Object.class;
    }

    public static <T> T box(T a) {
        return a;
    }

    /**
     * {@code out.writeInt(value)} in an {@code int} specialization (and {@code writeLong}, {@code writeDouble}, …).
     * The generic fallback writes the object with {@link ObjectOutput#writeObject} when possible, otherwise as a
     * length-prefixed Java-serialized byte block (see {@link #serialize}).
     */
    public static <T> void write(DataOutput out, T value) throws IOException {
        if (out instanceof ObjectOutput oo) {
            oo.writeObject(value);
        } else {
            byte[] bytes = serialize(value);
            out.writeInt(bytes.length);
            out.write(bytes);
        }
    }

    /**
     * {@code in.readInt()} in an {@code int} specialization. The generic fallback mirrors {@link #write} with Java
     * deserialization under a size and depth limit; it accepts any class, so it must only read trusted input.
     */
    @SuppressWarnings("unchecked")
    public static <T> T read(DataInput in) throws IOException {
        if (in instanceof ObjectInput oi) {
            try {
                return (T) oi.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
        }
        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        return deserialize(bytes);
    }

    /** {@code buffer.putInt(value)} in an {@code int} specialization ({@code put((byte) (v ? 1 : 0))} for boolean). */
    public static <T> void put(ByteBuffer buffer, T value) {
        try {
            byte[] bytes = serialize(value);
            buffer.putInt(bytes.length);
            buffer.put(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code buffer.getInt()} in an {@code int} specialization ({@code get() != 0} for boolean). */
    public static <T> T get(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.getInt()];
        buffer.get(bytes);
        try {
            return deserialize(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] bytes) throws IOException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(LIMITS);
            return (T) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    /** {@code Integer.BYTES} in an {@code int} specialization ({@code 1} for boolean); {@code -1} (unknown) here. */
    public static int bytes() {
        return -1;
    }
}
