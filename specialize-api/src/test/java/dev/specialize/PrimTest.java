package dev.specialize;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

/** The generic fallbacks; in primitive specializations every call is replaced by the processor. */
class PrimTest {

    @Test
    void genericValueHelpers() {
        assertNull(Prim.<String>zero());
        assertTrue(Prim.eq("a", "a"));
        assertFalse(Prim.eq("a", null));
        assertEquals("a".hashCode(), Prim.hash("a"));
        assertEquals(0, Prim.hash(null));
        assertEquals("null", Prim.str(null));
        assertEquals("x", Prim.str("x"));
        assertTrue(Prim.compare("a", "b") < 0);
        Object[] array = Prim.<Object>newArray(3);
        assertEquals(3, array.length);
        assertTrue(Prim.isNull(null));
        assertFalse(Prim.isNull(""));
        assertFalse(Prim.isPrimitive());
        assertEquals(Object.class, Prim.type());
        assertEquals("v", Prim.box("v"));
        assertEquals(-1, Prim.bytes());
        assertEquals(2, BoxedArguments.values().length);
    }

    @Test
    void genericIoUsesObjectStreamsOrSerializedBlocks() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            Prim.write(out, "payload");
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals("payload", Prim.<String>read(in));
        }
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        Prim.write(new DataOutputStream(plain), java.util.List.of("a", "b"));
        assertEquals(java.util.List.of("a", "b"), Prim.read(new DataInputStream(new ByteArrayInputStream(plain.toByteArray()))));

        ByteBuffer buffer = ByteBuffer.allocate(256);
        Prim.put(buffer, "in a buffer");
        buffer.flip();
        assertEquals("in a buffer", Prim.get(buffer));
        assertThrows(UncheckedIOException.class, () -> Prim.put(ByteBuffer.allocate(256), new Object()), "not serializable");
        ByteBuffer corrupt = ByteBuffer.allocate(8).putInt(3).put(new byte[]{1, 2, 3}).flip();
        assertThrows(UncheckedIOException.class, () -> Prim.get(corrupt));

        ByteArrayOutputStream corruptStream = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(corruptStream)) {
            out.writeObject(new Missing());
        }
        byte[] data = corruptStream.toByteArray();
        String name = Missing.class.getName();
        int at = indexOf(data, name.getBytes());
        System.arraycopy("dev.specialize.PrimTest$Gone___".getBytes(), 0, data, at, name.length());
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            IOException failure = assertThrows(IOException.class, () -> Prim.read(in));
            assertTrue(failure.getCause() instanceof ClassNotFoundException);
        }
        ByteBuffer unknownClass = ByteBuffer.allocate(data.length + 4).putInt(data.length).put(data).flip();
        UncheckedIOException viaBuffer = assertThrows(UncheckedIOException.class, () -> Prim.get(unknownClass));
        assertTrue(viaBuffer.getCause().getCause() instanceof ClassNotFoundException);
        assertArrayEquals(new byte[0], new byte[0]);
    }

    static final class Missing implements java.io.Serializable {
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new IllegalStateException("not found");
    }
}
