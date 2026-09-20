# `@Specialize` on a static method

```java
public final class Vec {
    @Specialize(types = {int.class, long.class, double.class})
    public static <T> T fold(T[] xs, T zero, BinaryOperator<T> plus) { … }
    // adds fold(int[], int, IntBinaryOperator), fold(long[], …), fold(double[], …)

    @Specialize(types = int.class)
    public static <T> String describe(Function<T, String> label, List<T> xs) { … }
    // adds describeInt(IntFunction<String>, List<Integer>)
}
```

The substitution is the one [templates](templates.md) use, applied to a single method: `Prim` helpers,
functional interfaces and `T[]` included. Overload resolution picks the specialized overload for primitive
arguments.

When no parameter is a bare `T` or `T[]`, the overload would have the same erasure as the original, so it
takes the `namePattern` name instead (`describeInt`).

The method must be `static`, declare exactly one type parameter, and sit outside a template class. Only
`types` and `namePattern` apply.
