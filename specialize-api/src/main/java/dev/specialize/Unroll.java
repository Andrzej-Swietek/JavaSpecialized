package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Unrolls every counted loop with literal bounds in the annotated method: {@code for (int i = 0; i < 4; i++) body}
 * becomes four copies of {@code body}, each with {@code i} replaced by its value, so javac sees constants where it
 * saw a variable (array indices, shifts, {@code @Inline} arguments become compile-time constants). Recognized loops:
 * {@code int} variable declared in the header with a literal start, condition {@code i < / <= / > / >= / != literal},
 * step {@code i++}, {@code i--}, {@code i += literal} or {@code i -= literal}. Loops of any other shape stay loops.
 * Compile errors: a body that assigns or redeclares the loop variable, a {@code break} / {@code continue} that
 * targets the loop, more than {@link #max()} iterations, or no unrollable loop at all.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Unroll {
    /** Must be an int literal; any other expression leaves the default in place. */
    int max() default 64;
}
