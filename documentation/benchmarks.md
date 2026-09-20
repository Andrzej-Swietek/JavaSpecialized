# Benchmarks

A standalone JMH build against the published artifacts:

```bash
./gradlew publishToMavenLocal
cd benchmarks && ./gradlew jmh
```

One run on a laptop, JDK 25, 1 fork, 5 × 1 s. Read the direction, not the digits.

| benchmark | what | result |
|---|---|---|
| `DictBenchmark` | 10 000 lookups by `int` key | `DictInt<String>` 31 µs vs `HashMap<Integer, String>` 75 µs |
| `OptBenchmark` | summing 1 000 000 values of the examples' `Opt<T>` with `getOrElse` | `OptInt` 3.2 ms vs `@Boxed Opt<Integer>` 4.0 ms vs `Optional<Integer>` 3.7 ms |
| `InlineBenchmark` | `Vec.sumOf(data, x -> x * 2)` over 100 000 ints | inlined lambda 6.8 µs vs `IntUnaryOperator` 7.2 µs |

Primitive-keyed structures win clearly where the JDK boxes on every access. The optional wins moderately
once the values are in memory; the boxed run also pays for the boxes during setup, which JMH does not
measure here. Lambda inlining changes nothing for a monomorphic call the JIT already inlines — its value is
in bytecode size, cold code and megamorphic call sites.

The memory difference lives in the arrays: a million `OptInt` is a million objects holding an `int`, a
million `Opt<Integer>` is that plus a million `Integer`s.
