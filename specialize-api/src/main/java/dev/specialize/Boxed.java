package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Keeps this one type use generic: {@code @Boxed Opt<Integer> x} stays the generic class, not {@code OptInt}. Allowed
 * on fields, locals, parameters, return types and nested type uses ({@code List<@Boxed Opt<Integer>>}).
 */
@Target({ElementType.TYPE_USE, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface Boxed {
}
