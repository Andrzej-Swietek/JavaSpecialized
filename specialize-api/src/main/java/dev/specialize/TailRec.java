package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Scala's {@code @tailrec} for Java: the annotated method must be {@code static}, {@code private} or {@code final}
 * (nothing can override it) and every call to itself must be in tail position — {@code return gcd(b, a % b);}, the
 * branches of a {@code return c ? f(x) : y;}, or the last statement of a {@code void} body. The processor turns the body
 * into a loop: parameters are reassigned (through temporaries, so {@code f(b, a)} swaps correctly) and the loop
 * continues, so the recursion needs no stack. A recursive call anywhere else, inside {@code try} / {@code synchronized},
 * a {@code final} parameter, or an overload of the same arity is a compile error, exactly like Scala refuses it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface TailRec {
}
