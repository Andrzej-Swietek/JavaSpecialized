package dev.specialize.examples;

import dev.specialize.Prim;
import dev.specialize.Specialize;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Binary codec for {@link Opt} values, the kind of thing a Kafka (de)serializer is made of. One template, specialized
 * together with {@code Opt}: {@code OptCodecInt.write(out, OptInt)} compiles to {@code out.writeInt(opt.get())},
 * {@code OptCodecLong.read(in)} to {@code in.readLong()} — no {@code Integer} / {@code Long} is ever created.
 *
 * <p>Composition of two templates: {@code Opt<T>} in this template becomes {@code Opt<Integer>} in {@code OptCodecInt},
 * which the client-side rewrite then turns into {@code OptInt}. Static bridges are injected here too, so
 * {@code OptCodec.write(out, Opt.some(5))} resolves to the {@code int} version; reading needs the type:
 * {@code OptCodec.<Integer>read(in)} or {@code OptCodecInt.read(in)}.
 */
@Specialize(types = {int.class, long.class, double.class, boolean.class})
public final class OptCodec<T> {
    private OptCodec() {
    }

    /** One presence byte followed by the value ({@link Prim#bytes()} bytes in a specialization). */
    public static int maxSize() {
        return 1 + Prim.bytes();
    }

    public static <T> void write(DataOutput out, Opt<T> opt) throws IOException {
        out.writeBoolean(opt.isDefined());
        if (opt.isDefined()) {
            Prim.write(out, opt.get());
        }
    }

    public static <T> Opt<T> read(DataInput in) throws IOException {
        return in.readBoolean() ? Opt.some(Prim.<T>read(in)) : Opt.empty();
    }

    public static <T> void put(ByteBuffer buffer, Opt<T> opt) {
        buffer.put((byte) (opt.isDefined() ? 1 : 0));
        if (opt.isDefined()) {
            Prim.put(buffer, opt.get());
        }
    }

    public static <T> Opt<T> get(ByteBuffer buffer) {
        return buffer.get() != 0 ? Opt.some(Prim.<T>get(buffer)) : Opt.empty();
    }
}
