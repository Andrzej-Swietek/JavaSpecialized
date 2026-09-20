# Templates

`@Specialize` on a top-level class, record or interface. For every type in `types` a specialization is
generated in the same package, named by `namePattern` (default `{Name}{Type}`, so `BoxInt`, `BoxChar`,
`BoxString`). `types` defaults to all eight primitives and also accepts plain reference types.

## What a specialization replaces

For a template `Box<T>` specialized to `int`:

| in the template | in `BoxInt` |
|---|---|
| `T` as field, parameter, return, variable, array element | `int`, `int[]` |
| `T` as a type argument or bound: `List<T>`, `? extends T`, `<R extends T>` | `List<Integer>` (boxed) |
| `(T) expr` | `(Integer) expr` |
| `Box<T>`, `Box<?>`, `new Box<>(..)`, `new Box[n]`, `Box.of(..)` in a self-typed return or initializer | `BoxInt`, `new BoxInt(..)`, `new BoxInt[n]`, `BoxInt.of(..)` |
| `Box<U>` for another type variable | stays the generic `Box` |
| `Function<T,R>` / `Function<A,T>` / `Function<T,T>` | `IntFunction<R>` / `ToIntFunction<A>` / `IntUnaryOperator` |
| `Predicate<T>`, `Consumer<T>`, `Supplier<T>`, `UnaryOperator<T>`, `BinaryOperator<T>` | `IntPredicate`, `IntConsumer`, `IntSupplier`, `IntUnaryOperator`, `IntBinaryOperator` |
| `static <T> Box<T> of(T v)` — a static method reusing the class parameter's name | `static BoxInt of(int v)` |
| [`Prim`](prim.md) calls | `==`, `0`, `new int[n]`, `Integer.hashCode(v)`, … |

Method names follow the interface: `apply` becomes `applyAsInt`, `get` becomes `getAsInt`. The mapping
covers `int`, `long`, `double` and `BooleanSupplier`; other primitives keep the boxed interface.

A hand-written overload in the template, such as `static BoxInt of(int v)`, stays in the template and is
dropped from the generated class where it would collide with the specialized method.

## Composition

A template may use another template. A substituted `T` inside a second template is marked primitive, so
`Box<T>` inside `Codec<T>` becomes `Box<int>` in `CodecInt`, which is then rewritten to `BoxInt`:

```java
@Specialize(types = {int.class, long.class})
public final class Codec<T> {
    public static <T> void write(DataOutput out, Box<T> box) throws IOException {
        Prim.write(out, box.get());        // CodecInt: out.writeInt(box.get())
    }
}
```

Reading needs the type at the call site: `Codec.<int>read(in)` or `CodecInt.read(in)`.

## Several type parameters

```java
@Specialize
public final class Dict<@Specialize.Param(types = {int.class, long.class}) K, V> {
    private K[] keys = Prim.newArray(8);                    // int[] in DictInt<V>
    public static <K, V> Dict<K, V> of(K key, V value) { … }
}
```

`@Specialize.Param` marks the parameters to substitute; the others stay type parameters of the generated
class, so `Dict<int, String>` is `DictInt<String>`. Several marked parameters produce every combination
(`PairIntLong`, `PairDoubleLong`), annotated `@Specialized(of = Pair.class, types = {int.class, long.class})`.

A self reference must repeat the template's own variables in the specialized positions: `Dict<K, V>` and
`Dict<?, V>` are rewritten, `Pair<V, K>` needs the swapped combination to exist. A functional interface over
two different primitives, such as `Function<K, V>`, stays boxed, because the JDK has no primitive interface
for every pair.

## `autoscan`

```java
@Specialize(autoscan = true)
public final class Box<T> { … }
```

Usage decides which specializations exist: every `Box<int>`, `Box<Integer>` or `Box.<long>of(..)` in the
compiled sources adds that primitive. Reference types are not inferred, and usage in another module does
not add anything — the template's own module decides. Combine with `types` for the union of both.

## `standsFor`: JDK spellings

```java
@Specialize(types = {int.class, long.class}, standsFor = {List.class, ArrayList.class})
public final class MyList<T> implements Iterable<T> { … }

List<int> primes = new ArrayList<>();     // MyListInt primes = new MyListInt();
```

Only the primitive spelling is redirected; `List<Integer>` stays the JDK list. The template has to offer
the constructors and methods the client calls, and javac reports the rest as ordinary errors. Two templates
standing for the same type is a compile error. Inside a template, `List<T>` is the JDK list.

A library publishes its templates as `META-INF/specialize/<name>.java`, so a consumer gets `List<int>`
without naming `MyList`.
