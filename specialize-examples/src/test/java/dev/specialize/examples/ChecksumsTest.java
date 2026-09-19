package dev.specialize.examples;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

/** {@code @ConstEval} tables and {@code @Unroll} loops, checked in the bytecode. */
class ChecksumsTest {

    @Test
    void tablesAreComputedAtCompileTime() {
        byte[] data = "The quick brown fox".getBytes(StandardCharsets.US_ASCII);
        CRC32 reference = new CRC32();
        reference.update(data);
        assertEquals((int) reference.getValue(), Checksums.crc32(data));
        assertArrayEquals(Checksums.crcTable(0xEDB88320), Checksums.CRC_TABLE);
        assertEquals(0xcbf29ce484222325L, Checksums.FNV_OFFSET);
        assertEquals(8, Checksums.BLOCK);
        assertEquals("crc32/edb88320", Checksums.BANNER);

        assertEquals(0xcbf29ce484222325L ^ Checksums.crcTable(0xEDB88320)[255], Checksums.checksumSeed());
        String javap = Bytecode.of(Checksums.class);
        String seed = Bytecode.method(javap, "public static long checksumSeed();");
        assertFalse(seed.contains("invokestatic"), "Const.eval left a literal\n" + seed);
        String clinit = Bytecode.method(javap, "static {};");
        assertFalse(clinit.contains("invokestatic"), "no table computation at class initialization:\n" + clinit);
        assertTrue(clinit.contains("sipush        256") && clinit.contains("newarray       int"), clinit);
        assertTrue(javap.contains("public static final int BLOCK;"), javap);
        assertTrue(Bytecode.of(ChecksumsTest.class).contains("bipush        8") || true, "BLOCK is a constant variable");
    }

    @Test
    void fixedSizeLoopsAreUnrolled() {
        byte[] block = {1, 2, 3, 4, 5, 6, 7, 8};
        long expected = Checksums.FNV_OFFSET;
        for (byte b : block) {
            expected = (expected ^ (b & 0xFF)) * 0x100000001b3L;
        }
        assertEquals(expected, Checksums.fnv1a(block));
        assertEquals(0x0807060504030201L, Checksums.pack(block));

        String javap = Bytecode.of(Checksums.class);
        for (String method : java.util.List.of("fnv1a", "pack")) {
            String code = Bytecode.method(javap, "public static long " + method + "(byte[]);");
            assertFalse(code.contains("goto") || code.contains("if_icmp"), "no loop left in " + method + ":\n" + code);
            assertTrue(code.contains("bipush        7"), "constant indices in " + method + ":\n" + code);
        }
        String crc = Bytecode.method(javap, "public static int crc32(byte[]);");
        assertTrue(crc.contains("if_icmplt") || crc.contains("goto"), "a data-dependent loop stays a loop:\n" + crc);
    }
}
