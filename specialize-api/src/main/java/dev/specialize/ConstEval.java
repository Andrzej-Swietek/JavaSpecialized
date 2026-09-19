package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Evaluates a {@code static final} field's initializer at compile time and replaces it with the literal result:
 * {@code @ConstEval static final int[] CRC = buildTable(0xEDB88320)} is compiled as {@code new int[]{0, 1996959894, …}},
 * {@code @ConstEval static final long MASK = compute()} becomes a constant variable that javac inlines at every use.
 * The processor compiles the declaring source file on its own (with the compilation's class path and, for a
 * conventional {@code src/…/pkg} layout, the same source root), loads it and reads the field, so the initializer may
 * call anything reachable from there; it runs the class's static initialization once, at compile time. Supported
 * values: primitives and their boxes, {@code String}, enums, {@code null}, and arrays (nested) of those. A field that is
 * not {@code static final}, an initializer that does not compile or throws, or a value of any other type is a compile
 * error carrying the underlying diagnostics.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface ConstEval {
}
