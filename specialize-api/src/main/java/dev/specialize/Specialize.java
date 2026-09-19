package dev.specialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a generic class as a <em>specialization template</em>: with one type parameter that parameter is specialized,
 * with several the ones marked {@link Param}. On a static generic method, see below.
 *
 * <p>For every type listed in {@link #types()} the processor generates a copy of the class in the same package,
 * named according to {@link #namePattern()} (e.g. {@code Opt<T>} → {@code OptInt}, {@code OptLong}, {@code OptString}),
 * with the type parameter substituted:
 * <ul>
 *   <li>{@code T} in variable, field, parameter, return and array positions becomes the primitive ({@code int}),</li>
 *   <li>{@code T} as a type argument ({@code List<T>}) becomes the box ({@code List<Integer>}),</li>
 *   <li>{@code Opt<T>} / {@code Opt<?>} (self references) become {@code OptInt},</li>
 *   <li>{@code java.util.function} interfaces over {@code T} become their primitive variants
 *       ({@code Predicate<T>} → {@code IntPredicate}, {@code Supplier<T>} → {@code IntSupplier} with {@code get()} →
 *       {@code getAsInt()} …),</li>
 *   <li>calls to {@link Prim} helpers are replaced by primitive-specific expressions
 *       ({@code Prim.zero()} → {@code 0}, {@code Prim.eq(a, b)} → {@code a == b} …).</li>
 * </ul>
 *
 * <p>Static methods that declare a type variable with the <em>same name</em> as the class type parameter
 * ({@code static <T> Opt<T> some(T v)}) are treated as part of the template and specialized too. For every such
 * factory whose erasure changes, a bridge overload is injected into the generic class itself
 * ({@code static OptInt some(int v)}), so {@code Opt.some(5)} resolves to the primitive specialization
 * without boxing and without any call-site rewriting.
 *
 * <p>In client code every occurrence of {@code Opt<Integer>} or {@code Opt<int>} is rewritten to {@code OptInt}
 * before attribution (Lombok-style AST rewriting), together with the initializer / return expression it targets.
 *
 * <p>A hand-written class annotated with {@link Specialized} for the same (template, type) pair takes precedence
 * over the generated one.
 *
 * <p>On a static generic method, one overload per type is generated next to it:
 * {@code @Specialize(types = {int.class, long.class}) static <T> T sum(T[] xs, BinaryOperator<T> plus, T zero)} gains
 * {@code static int sum(int[] xs, IntBinaryOperator plus, int zero)} and the {@code long} twin. When no parameter is a
 * bare {@code T} or {@code T[]} the overload is named by {@link #namePattern()} ({@code joinInt}). Only {@link #types()}
 * and {@link #namePattern()} apply to methods.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Specialize {

    /** Types to specialize for. Primitive class literals and plain (non-generic) reference types are accepted. */
    Class<?>[] types() default {
            int.class, long.class, double.class, boolean.class,
            byte.class, short.class, char.class, float.class
    };

    /**
     * Discover the primitive specializations from usage instead of (or in addition to) listing them: every
     * {@code Opt<Integer>}, {@code Opt<int>} or {@code Opt.<Long>m()} in the sources being compiled adds that primitive
     * to {@link #types()}. When {@code types} is not given explicitly, only the scanned primitives are generated.
     * Reference types are never inferred; list them. A usage that only appears in a later compilation (another module)
     * simply keeps the generic class, so scanning is safe but the template's own module decides what exists.
     */
    boolean autoscan() default false;

    /**
     * Whether {@code Opt<Integer>} in client code means the specialization or the generic class. By default it does
     * ({@link BoxedArguments#SPECIALIZE}): client code then type-checks in every IDE as ordinary generics and still
     * compiles to the primitive class; {@link Boxed} opts a single use out. {@link BoxedArguments#KEEP} reserves that
     * for the {@code Opt<int>} spelling. Reference specializations ({@code Opt<User>}) are always selected.
     */
    BoxedArguments boxedArguments() default BoxedArguments.SPECIALIZE;

    /**
     * Marks the type parameters to specialize when a template has more than one; the others stay generic in the
     * generated class: {@code class Map2<@Specialize.Param(types = {int.class, long.class}) K, V>} yields
     * {@code Map2Int<V>} and {@code Map2Long<V>}, and {@code Map2<Integer, String>} becomes {@code Map2Int<String>}.
     * Several marked parameters produce every combination ({@code PairIntLong}). A single-parameter template needs no
     * {@code Param}: the class-level {@link #types()} apply to it.
     */
    @Target(ElementType.TYPE_PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Param {
        Class<?>[] types() default {
                int.class, long.class, double.class, boolean.class,
                byte.class, short.class, char.class, float.class
        };
    }

    /**
     * JDK types this template stands in for at use sites, written with the primitive spelling:
     * {@code @Specialize(standsFor = {List.class, ArrayList.class}) class MyList<T>} makes {@code List<int> xs = new ArrayList<>()}
     * compile to {@code MyListInt xs = new MyListInt()}, and {@code Map<int, V>} with {@code standsFor = {Map.class, HashMap.class}}
     * to {@code DictInt<V>}. Only the primitive spelling is affected: {@code List<Integer>} stays the JDK list. The
     * template must offer the constructors and methods the client calls; the compiler reports the rest.
     */
    Class<?>[] standsFor() default {};

    /**
     * Name of the generated class. {@code {Name}} is the template's simple name, {@code {Type}} is the capitalized
     * simple name of the target type ({@code Int}, {@code Long}, {@code Char}, {@code Boolean}, {@code String} …), or the
     * concatenation for several specialized parameters ({@code IntLong}).
     */
    String namePattern() default "{Name}{Type}";
}
