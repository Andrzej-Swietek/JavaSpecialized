package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the static no-argument factory of a template (or a specialization) that stands for "no value":
 * {@code @Absent public static <T> Opt<T> empty()}. Codecs such as the Jackson module use it for a JSON {@code null}
 * and for a missing field, so {@code Opt<int>} components of a message never end up as {@code null} references.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Absent {
}
