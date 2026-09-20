# Getting started

## Requirements

JDK 25. The processor uses `com.sun.tools.javac` internals, which `jdk.compiler` does not export.

## Gradle

```kotlin
dependencies {
    implementation("dev.specialize:specialize-api:0.1.0")
    annotationProcessor("dev.specialize:specialize-processor:0.1.0")
    testAnnotationProcessor("dev.specialize:specialize-processor:0.1.0")
}
```

## Maven

`specialize-api` as a dependency, `specialize-processor` in `maven-compiler-plugin`'s
`<annotationProcessorPaths>`.

## The module warning

At startup the processor opens the javac packages for itself through `sun.misc.Unsafe`, the way Lombok
does. On JDK 24 and later that prints:

```
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
```

Exporting the packages yourself skips that path entirely:

```
-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
```

and the same for `.util`, `.code`, `.processing`, `.parser`, `.api`, `.comp`. In Maven put them in
`.mvn/jvm.config`; in Gradle set `options.isFork = true` and pass them in `forkOptions.jvmArgs`.

## First template

```java
@Specialize(types = {int.class, long.class})
public final class Box<T> {
    private final T value;
    private Box(T value) { this.value = value; }
    public static <T> Box<T> of(T value) { return new Box<>(value); }
    public T get() { return value; }
}
```

After a build, `BoxInt` and `BoxLong` are in `build/generated/sources/annotationProcessor`, with an
`int` and a `long` field. Client code writes `Box<int>` or `Box<Integer>`; both compile to `BoxInt`.

## In a real application

`samples/spring-boot-app` is a standalone Gradle build that takes the processor from a Maven repository,
next to Lombok, with a Kafka-style record, a `@Service`, a REST endpoint and a `@SpringBootTest`:

```bash
./gradlew publishToMavenLocal
cd samples/spring-boot-app && ./gradlew test
```

## Seeing what the processor did

`-Aspecialize.dump=<dir>` writes every compilation unit as the processor left it: rewritten types,
injected bridges, inlined calls, unrolled loops, evaluated constants. It is the equivalent of
delombok.

```kotlin
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Aspecialize.dump=${layout.buildDirectory.get()}/specialize-dump")
}
```
