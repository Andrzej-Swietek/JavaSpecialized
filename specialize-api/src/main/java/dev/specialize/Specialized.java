package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated class is the specialization of template {@link #of()} for type {@link #type()}.
 *
 * <p>Generated specializations carry this annotation with {@link #generated()} {@code = true}. A hand-written class
 * with {@code generated = false} is an <em>explicit specialization</em>: the processor does not generate a class for
 * that (template, type) pair and rewrites client usages such as {@code Opt<User>} to the annotated class instead.
 *
 * <p>For discovery from already compiled jars, explicit specializations should live in the template's package and
 * follow the template's {@link Specialize#namePattern()} (e.g. {@code OptUser}). Within a single compilation any
 * name works.
 *
 * <p>The explicit class should expose the same static factories as the template for the ones you want the injected
 * bridges ({@code Opt.some(user)}) to delegate to; a factory that is missing is simply not bridged.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Specialized {
    Class<?> of();

    /** The specialized type; for templates with several specialized parameters use {@link #types()} instead. */
    Class<?> type() default void.class;

    /** One type per specialized parameter of the template, in declaration order. */
    Class<?>[] types() default {};

    boolean generated() default false;
}
