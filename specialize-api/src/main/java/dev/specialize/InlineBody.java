package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal: the processor emits it on {@link Inline} methods. Consumers compiling against the class file inline
 * calls from what it stores. Do not write it by hand.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InlineBody {
    String[] params();

    /** Source of each parameter type, or "" when the type mentions a method type variable (no cast inserted). */
    String[] paramTypes();

    /** Source of the return type when it is primitive, otherwise "". */
    String returnType() default "";

    /** Fully qualified source of the body: an expression, or a block starting with {@code {}. */
    String body();

    boolean isVoid() default false;
}
