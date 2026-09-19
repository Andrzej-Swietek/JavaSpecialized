package dev.specialize.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.specialize.Boxed;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodecAndBoxedTest {

    /** A message the way it would be sent through Kafka: primitive-backed optionals, no boxing in the object graph. */
    record OrderEvent(long orderId, Opt<int> quantity, Opt<double> discount, Opt<boolean> express) {
        static OrderEvent read(DataInputStream in) throws IOException {
            return new OrderEvent(in.readLong(), OptCodec.<int>read(in), OptCodecDouble.read(in), OptCodec.<boolean>read(in));
        }

        void write(DataOutputStream out) throws IOException {
            out.writeLong(orderId);
            OptCodec.write(out, quantity);   // bridge → OptCodecInt.write(DataOutput, OptInt)
            OptCodec.write(out, discount);   // bridge → OptCodecDouble.write
            OptCodec.write(out, express);    // bridge → OptCodecBoolean.write
        }
    }

    @Test
    void recordFieldsAreSpecializedAndRoundTripThroughDataStreams() throws Exception {
        assertEquals(OptInt.class, OrderEvent.class.getRecordComponents()[1].getType());
        assertEquals(OptDouble.class, OrderEvent.class.getRecordComponents()[2].getType());
        assertEquals(OptBoolean.class, OrderEvent.class.getRecordComponents()[3].getType());

        OrderEvent event = new OrderEvent(42L, Opt.some(7), Opt.empty(), Opt.some(true));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        event.write(new DataOutputStream(bytes));
        assertEquals(Long.BYTES + (1 + Integer.BYTES) + 1 + (1 + 1), bytes.size());
        assertEquals(1 + Integer.BYTES, OptCodecInt.maxSize());
        assertEquals(1 + 1, OptCodecBoolean.maxSize());

        OrderEvent back = OrderEvent.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(event, back);
        assertEquals(7, back.quantity().get());
    }

    @Test
    void byteBufferVariant() {
        ByteBuffer buffer = ByteBuffer.allocate(OptCodecLong.maxSize() + OptCodecBoolean.maxSize());
        OptCodec.put(buffer, Opt.some(1L << 40));
        OptCodec.put(buffer, Opt.<boolean>empty());
        buffer.flip();
        assertEquals(OptLong.some(1L << 40), OptCodecLong.get(buffer));
        assertEquals(OptBoolean.empty(), OptCodec.<boolean>get(buffer));
    }

    @Test
    void codecBytecodeUsesPrimitiveIo() {
        String javap = Bytecode.of(OptCodecInt.class);
        assertTrue(javap.contains("DataOutput.writeInt:(I)V"), javap);
        assertTrue(javap.contains("DataInput.readInt:()I"), javap);
        assertTrue(javap.contains("ByteBuffer.putInt:(I)"), javap);
        assertFalse(javap.contains("Integer.valueOf"), javap);
        assertFalse(javap.contains("Integer.intValue"), javap);
        assertFalse(javap.contains("dev/specialize/Prim"), "Prim helpers fully replaced: " + javap);

        String bridges = Bytecode.of(OptCodec.class);
        assertTrue(bridges.contains("static void write(java.io.DataOutput, dev.specialize.examples.OptInt)"), bridges);
        assertTrue(bridges.contains("static void put(java.nio.ByteBuffer, dev.specialize.examples.OptBoolean)"), bridges);
    }

    // String is a listed reference specialization, so Opt<String> always means OptString — unless @Boxed
    @Boxed
    Opt<String> boxedField = Opt.fromOptional(java.util.Optional.of("field"));

    @Boxed
    static Opt<String> boxedReturn() {
        return Opt.fromOptional(java.util.Optional.empty());
    }

    @Test
    void boxedKeepsTheGenericClassForReferenceSpecializations() {
        @Boxed Opt<String> local = Opt.fromOptional(java.util.Optional.of("local"));
        List<@Boxed Opt<String>> nested = List.of(Opt.some(1).map(String::valueOf)); // map() yields the generic Opt
        Opt<String> specialized = Opt.some("s");
        @Boxed Opt<Integer> boxedByDefault = Opt.fromOptional(java.util.Optional.of(1));

        assertSame(Opt.class, ((Object) local).getClass());
        assertSame(Opt.class, ((Object) boxedField).getClass());
        assertSame(Opt.class, ((Object) boxedReturn()).getClass());
        assertSame(Opt.class, ((Object) nested.get(0)).getClass());
        assertSame(Opt.class, ((Object) boxedByDefault).getClass());
        assertSame(OptString.class, ((Object) specialized).getClass());
    }
}
