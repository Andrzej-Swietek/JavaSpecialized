# specialize

A javac annotation processor that generates primitive specializations of generic classes, so
`Box<int>` is a class with an `int` field instead of `Box<Integer>` with a boxed one. It also inlines
methods, evaluates constants and rewrites tail recursion and counted loops at compile time.

It runs inside javac like Lombok, not as a build plugin, so Maven and Gradle need no extra setup.
For what an IDE sees, read [IDE support](documentation/ide.md).

```java
@Specialize(types = {int.class, long.class, String.class})
public final class Box<T> {
    private final T value;
    private Box(T value) { this.value = value; }
    public static <T> Box<T> of(T value) { return new Box<>(value); }
    public T get() { return value; }
}
```

```java
Box<int> b = Box.of(5);     // BoxInt b = BoxInt.of(5);  — an int field, no Integer allocated
int v = b.get();            // BoxInt.get()I
record Order(long id, Box<int> quantity) { }        // primitive-backed record component
```

`BoxInt`, `BoxLong` and `BoxString` are generated next to the template and land in
`build/generated/sources/annotationProcessor`.

## Features

| | |
|---|---|
| [`@Specialize`](documentation/templates.md) | a specialization per primitive, for one or several type parameters |
| [`@Specialized`, `@SpecializeWith`](documentation/explicit-specializations.md) | hand-written specializations, and specializing a library template for your own type |
| [call sites](documentation/call-sites.md) | `Box<int>` in any type position, bridges, `@Boxed` opt-out, pattern matching |
| [`@Specialize` on methods](documentation/methods.md) | an overload per primitive for one static generic method |
| [`@Inline`](documentation/inline.md) | the body replaces the call, lambdas included, so javac folds it |
| [`@TailRec`](documentation/tailrec.md) | tail recursion as a loop, with Scala's rules |
| [`@Unroll`](documentation/unroll.md) | counted loops with literal bounds, unrolled |
| [`@ConstEval`, `Const.eval`](documentation/consteval.md) | initializers and expressions evaluated during compilation |
| [`Prim`](documentation/prim.md) | helpers that become `==`, `0`, `new int[n]` in a specialization |
| [Jackson module](documentation/jackson.md) | JSON `null` and missing fields become the `@Absent` value |
| [IntelliJ plugin](documentation/ide.md) | generated members visible in the IDE, no red `Box<int>` |

## Install

Requires JDK 25.

```kotlin
dependencies {
    implementation("dev.specialize:specialize-api:0.1.0")
    annotationProcessor("dev.specialize:specialize-processor:0.1.0")
    testAnnotationProcessor("dev.specialize:specialize-processor:0.1.0")
}
```

The artifacts are not on Maven Central yet; `./gradlew publishToMavenLocal` puts them in `~/.m2`.

[Getting started](documentation/getting-started.md) covers Maven, the `jdk.compiler` warning and how to see
the rewritten sources.

## Build

```bash
./gradlew build     # 125 tests, 100% line and branch coverage in every module
```

Modules: `specialize-api` (the annotations), `specialize-processor`, `specialize-examples`,
`specialize-jackson`, `specialize-idea` (IntelliJ plugin, standalone build), plus `benchmarks/` and
`samples/spring-boot-app` which consume the published artifacts.

[How the processor works](documentation/internals.md) · [Limitations](documentation/limitations.md) ·
[tutorial/README.md](tutorial/README.md) (in Polish) on javac internals
