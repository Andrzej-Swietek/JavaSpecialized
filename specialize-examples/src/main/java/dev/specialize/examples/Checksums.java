package dev.specialize.examples;

import dev.specialize.Const;
import dev.specialize.ConstEval;
import dev.specialize.Unroll;

/** Lookup tables computed at compile time and fixed-size loops unrolled into straight-line code. */
public final class Checksums {
    /** 256 entries, evaluated once by the processor and compiled as an array literal: no work at class initialization. */
    @ConstEval
    public static final int[] CRC_TABLE = crcTable(0xEDB88320);

    @ConstEval
    public static final long FNV_OFFSET = Long.parseUnsignedLong("14695981039346656037");

    /** A constant variable: javac inlines it at every use. */
    @ConstEval
    public static final int BLOCK = Integer.parseInt("8");

    @ConstEval
    public static final String BANNER = "crc32/" + Integer.toHexString(0xEDB88320);

    private Checksums() {
    }

    static int[] crcTable(int polynomial) {
        int[] table = new int[256];
        for (int i = 0; i < table.length; i++) {
            int c = i;
            for (int k = 0; k < 8; k++) {
                c = (c & 1) != 0 ? polynomial ^ (c >>> 1) : c >>> 1;
            }
            table[i] = c;
        }
        return table;
    }

    /** The same thing inline: the argument is evaluated by the processor and becomes a literal in the method body. */
    public static long checksumSeed() {
        return Const.eval(Long.parseUnsignedLong("14695981039346656037") ^ crcTable(0xEDB88320)[255]);
    }

    public static int crc32(byte[] data) {
        int crc = 0xFFFFFFFF;
        for (byte b : data) {
            crc = CRC_TABLE[(crc ^ b) & 0xFF] ^ (crc >>> 8);
        }
        return ~crc;
    }

    /** One 8-byte block: the loop is unrolled, every index and shift is a constant. */
    @Unroll
    public static long fnv1a(byte[] block) {
        long hash = FNV_OFFSET;
        for (int i = 0; i < 8; i++) {
            hash = (hash ^ (block[i] & 0xFF)) * 0x100000001b3L;
        }
        return hash;
    }

    @Unroll
    public static long pack(byte[] block) {
        long word = 0;
        for (int i = 7; i >= 0; i--) {
            word = (word << 8) | (block[i] & 0xFF);
        }
        return word;
    }
}
