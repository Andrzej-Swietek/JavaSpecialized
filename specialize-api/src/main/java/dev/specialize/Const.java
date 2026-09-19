package dev.specialize;

/**
 * Compile-time evaluation of an expression: {@code Const.eval(crcTable(0xEDB88320))} anywhere in a method body is
 * replaced by the literal result, exactly like a {@code @ConstEval} field. The expression may use only static
 * members and constants (it is compiled outside the method) and must produce a primitive, {@code String}, enum,
 * {@code null} or an array of those. Without the processor it simply returns its argument.
 */
public final class Const {
    private Const() {
    }

    public static <T> T eval(T value) {
        return value;
    }
}
