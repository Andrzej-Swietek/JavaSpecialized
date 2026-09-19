package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static} method as inlinable: every call is replaced at compile time by its body, an expression or a
 * block ending in {@code return} (a void block for {@code void}), so javac can constant-fold it: {@code MathX.sq(3)} is {@code 9}.
 *
 * <ul>
 *   <li>arguments are cast to the declared parameter types, the result to a primitive return type;</li>
 *   <li>only pure arguments (identifiers, literals, and non-throwing operators over them) are substituted; a call with
 *       any other argument is left alone. A lambda argument is applied where the body calls it, given a parameter with
 *       a concrete declared type;</li>
 *   <li>the body may reference parameters, its own locals, public static members of the declaring class and
 *       resolvable types; lambdas and anonymous classes in the body are rejected;</li>
 *   <li>a block's locals are renamed, so they cannot clash with the caller's.</li>
 * </ul>
 *
 * <p>The body is recorded in an {@link InlineBody} annotation in the class file, so calls from other modules are inlined too.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inline {
}
