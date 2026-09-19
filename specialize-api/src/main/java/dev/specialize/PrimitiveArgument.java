package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal marker: the javac plugin turns {@code Opt<int>} into {@code Opt<@PrimitiveArgument java.lang.Integer>}
 * right after parsing. The processor rewrites the marked type to its specialization, and reports an error when
 * no specialization exists.
 */
@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.SOURCE)
public @interface PrimitiveArgument {
}
