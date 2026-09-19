# specialize

A Lombok-style javac annotation processor for Java 25 that

* **specializes generic classes for primitives** (`@Specialize`): `Opt<T>` becomes `OptInt`, `OptLong`, `OptDouble` …
  with real `int`/`long`/`double` fields and no boxing anywhere, while client code keeps writing `Opt<Integer>` and
  `Opt.some(5)`;
* lets you **hand-write the specialization for one particular `T`** (`@Specialized`), like an explicit template
  specialization in C++, and routes `Opt<User>` to it;
* handles **several type parameters** (`Dict<@Specialize.Param K, V>` → `DictInt<V>`, `Pair<K, V>` → `PairIntLong`);
* **inlines methods at compile time** (`@Inline`) so javac can constant-fold them: `MathX.sq(3)` compiles to `9`;
* **turns tail recursion into loops** (`@TailRec`), with Scala's rules and compile errors;
* **unrolls counted loops** (`@Unroll`) and **evaluates `static final` initializers at compile time** (`@ConstEval`):
  a CRC table becomes an array literal, `mask(40)` becomes a constant variable javac folds at every use.

Like Lombok it is *not* a Maven/Gradle plugin: it runs inside javac, modifies the ASTs of the classes being compiled
and generates the specialized sources, so it works with Maven, Gradle and IDEs alike. It touches
`com.sun.tools.javac` internals and opens the `jdk.compiler` module for itself the same way Lombok does.

```java
@Specialize(types = {int.class, long.class, double.class, boolean.class, String.class})
public final class Opt<T> implements Iterable<T> {           // template, modelled after AVSystem commons Opt[A]
    private final T value;
    private final boolean defined;
    public static <T> Opt<T> some(T value) { return new Opt<>(value, true); }
    public T get() { ... }
    public Opt<T> filter(Predicate<T> p) { return defined && p.test(value) ? this : empty(); }
    public boolean contains(T c) { return defined && Prim.eq(value, c); }
    ...
}
```

```java
Opt<int> a = Opt.some(5);                // compiled as:  OptInt a = OptInt.some(5);
Opt<int> b = Opt.<int>some(7);           // works in fields, parameters, returns, records and interfaces too
int sum = a.get() + b.get();             // OptInt.get()I — no Integer anywhere
@Boxed Opt<Integer> boxed = Opt.fromOptional(Optional.of(5)); // opt-out: the ordinary generic class
Opt<User> u = Opt.some(user);            // OptUser u = OptUser.some(user)   (hand-written explicit specialization)
List<Opt<int>> l = List.of(Opt.some(1));                         // List<OptInt>; Opt.some(int) is a bridge in Opt
record OrderEvent(long id, Opt<int> qty, Opt<double> discount) {}       // primitive-backed message fields
new OrderEvent(42L, Opt.some(7), Opt.empty());                   // Opt.empty() retargeted by the constructor's parameter
```

The generated `OptInt` (excerpt; the full files land in `specialize-examples/build/generated/...`):

```java
@dev.specialize.Specialized(of = Opt.class, type = int.class, generated = true)
@dev.specialize.GeneratedSpecialization
public final class OptInt implements Iterable<Integer> {
    private static final OptInt EMPTY = new OptInt(0, false);
    private final int value;
    private final boolean defined;
    public static OptInt some(int value) { return new OptInt(value, true); }
    public int get() { ... }
    public int getOrElse(IntSupplier other) { return defined ? value : other.getAsInt(); }
    public boolean contains(int candidate) { return defined && (value == candidate); }
    public OptInt filter(IntPredicate p) { return defined && p.test(value) ? this : empty(); }
    public OptInt transform(IntUnaryOperator f) { return defined ? some(f.applyAsInt(value)) : this; }
    public int[] toArray() { int[] array = new int[defined ? 1 : 0]; ... }
    public int hashCode() { return defined ? Integer.hashCode(value) : 0; }
}
```

## Modules and build

New to javac internals? `tutorial/README.md` (in Polish) walks through the compiler phases, `JCTree`,
`TreeScanner`/`TreeTranslator` and how each of our passes uses them.


| module | content |
|---|---|
| `specialize-api` | `@Specialize` (+ `Specialize.Param`), `@Specialized`, `@SpecializeWith`, `@Inline`, `@TailRec`, `@Unroll`, `@ConstEval`, `@Boxed`, the `Prim` helper — the only compile dependency of your code |
| `specialize-processor` | the javac processor: Lombok-style AST rewriting, generation, module opener |
| `specialize-examples` | `Opt<T>`, the explicit `OptUser`, `OptCodec<T>` (binary codec), `MyList<T>` (varargs, iterator, stands for `List`), `Dict<K, V>` (specialized key, generic value, stands for `Map`), `Vec` (`@Specialize` on methods), `Checksums` (`@ConstEval` tables, `@Unroll` loops), `MathX` (`@Inline`, `@TailRec`), a `Demo`, tests |
| `specialize-jackson` | `SpecializeModule`: JSON `null` / missing fields → the `@Absent` value of a specialization |
| `specialize-idea` | IntelliJ IDEA plugin (standalone Gradle build, see *IDE support*) |
| `benchmarks/`, `samples/` | JMH benchmarks and the Spring Boot sample: standalone builds consuming the published artifacts |

```
./gradlew build          # compiles, runs 96 tests, checks 100 % line and branch coverage (JaCoCo) in every module
java -cp specialize-examples/build/classes/java/main:specialize-api/build/classes/java/main dev.specialize.examples.Demo
```

Coverage is enforced by `jacocoTestCoverageVerification` (line and branch ratio 1.0). Generated specializations carry
`@GeneratedSpecialization`, which JaCoCo filters out automatically, so they do not count against the template's coverage.
The processor is tested by compiling snippets and the real examples in-process through `javax.tools`, both from source
and against previously compiled class files.

### Trying it in a real application (Spring Boot sample)

```
./gradlew publishToMavenLocal                 # dev.specialize:specialize-api:0.1.0 and :specialize-processor:0.1.0 into ~/.m2
cd samples/spring-boot-app && ./gradlew test  # a Spring Boot 3.5 app with Lombok next to the processor
```

`samples/spring-boot-app` is written the way a product team would write it: nothing in it knows about these sources,
the processor comes from the Maven repository. It has an `Opt<T>` template of its own, a Kafka-style
`record OrderEvent(long id, int quantity, Opt<int> promoCode, Opt<double> discount)`, a `@Service` with Lombok's
`@RequiredArgsConstructor` and `@Value` next to `@Inline` and `@TailRec`, a REST endpoint that reports the runtime
class of the field (`OptInt`), and a `@SpringBootTest` that starts the context and calls it. Its
`build/specialize-dump` shows the rewritten sources.

### Using it in your project

Gradle:

```kotlin
dependencies {
    implementation("dev.specialize:specialize-api:0.1.0")
    annotationProcessor("dev.specialize:specialize-processor:0.1.0")
    testAnnotationProcessor("dev.specialize:specialize-processor:0.1.0")
}
```

Maven: add `specialize-api` as a dependency and `specialize-processor` to `maven-compiler-plugin`'s
`<annotationProcessorPaths>`.

The processor needs `com.sun.tools.javac.*`, which `jdk.compiler` does not export. On start it opens those packages
for itself with the `sun.misc.Unsafe` / `IMPL_LOOKUP` trick that Lombok uses; on JDK 24+ that prints one
`WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`. To avoid the trick, export the packages
yourself (`-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED`, likewise `.util .code .processing
.parser .api .comp`; Maven: `.mvn/jvm.config`, Gradle: `options.isFork = true` + `forkOptions.jvmArgs`) and the opener
does nothing. `specialize-examples/build.gradle.kts` only silences the warning with `--sun-misc-unsafe-memory-access=allow`.

### Processor architecture

Everything below `dev.specialize.processor` is internal to the processor. javac trees are mutated in place (that is
what an AST rewriter does); everything else is immutable data (records, sealed hierarchies) and `Optional`-returning
lookups. 69 of the 77 files are under 150 lines; the largest is `TemplateSpecializer` at 276.

| package | content |
|---|---|
| `dev.specialize.processor` | `SpecializeProcessor` (entry), `SpecializePlugin` (auto-started, makes `Opt<int>` legal), `RoundProcessor` (one round: register → scan → rewrite → generate → bridge), `Javac`, `ModuleAccess`, `ProcessingEnvironments`, `Diagnostics`, `SourceDump` |
| `…processor.model` | the sealed model: `TargetType` (`PrimitiveTarget` / `ReferenceTarget`), `TargetTuple` (one target per specialized parameter), `Template` (`SourceTemplate` / `BinaryTemplate`, with `TypeParameter`s), `NamePattern`, `Specialization` (`GeneratedSpecialization` / `ExplicitSpecialization`) |
| `…processor.registry` | `SpecRegistry` (what exists, from sources and class files) with `SpecializationIndex` (lookup by name and by targets) and `Aliases` (`standsFor`), `AnnotationReader` (annotations → model, with the error messages), `AnnotationValues`, `TemplateSources` (template sources stored in and read from jars), `UsageScanner` (autoscan), `ClassIndex` (callee signatures) |
| `…processor.resolve` | `NameResolver` / `CompilationUnitResolver` (names before attribution), `ClassScope`, `Annotations` (the annotation names), `Match` (tree search), `TreeUtil` |
| `…processor.specialize` | `TemplateSpecializer` with `SpecializedClass` (the renamed, annotated copy), `Substitution` (what `T` becomes), `TranslationScope` (what is visible while translating), `BoxingDepth` (value vs type-argument position), `PrimCallRewriter` (`Prim.*`), `PrimHelper`, `FunctionalInterfaces`, `SpecializationAnnotations`, `MethodSignatures`, `SpecializationGenerator`, `SourceRenderer`, `BridgeInjector`, `MethodSpecializer`, `PrimitiveArgumentRewriter` |
| `…processor.rewrite` | `UseSiteRewriter` (types and the expressions that must produce them), `SpecLookup` (which specialization a tree denotes), `SpecUse` (how one is spelled and applied), `VariableScope`, `ScopedTranslator`, `Retargeter` |
| `…processor.inline` | `InlineRegistry`, `InlineMethodFactory`, `InlineBodyParser`, `InlineBodyValidator`, `InlineBodyStamp`, `Qualifier`, `InlineMethod` with `BodySubstitution`, `LambdaInliner`, `InlinedBlocks`, `LocalNames`, `InlineExpander` |
| `…processor.tailrec` | `TailRecursionEliminator` (validation), `TailCallLoop` (the loop transformation) |
| `…processor.unroll` | `LoopUnroller`, `ConstantLoop` (loop shape), `LoopBodyCheck`, `LoopVariableSubstituter` |
| `…processor.consteval` | `ConstEvalRewriter`, `ConstantEvaluator` (side compilation + reflection), `EvaluationResult`, `SideFileManager`, `Literals` |

## IDE support

IntelliJ, Eclipse and VS Code analyse sources with their own front ends, not with javac, so they see neither the
injected bridges nor the type rewriting — the same situation as Lombok without its IDE plugin. What works today:

* Generated classes (`OptInt`, `OptCodecInt`, `OptUserDTO`) are ordinary sources under
  `build/generated/sources/annotationProcessor`, which Gradle-imported projects register as a generated-sources root:
  after a build the IDE shows them with all members, like `delombok` output.
* With the default `SPECIALIZE` mode, client code spelled `Opt<Integer>` is valid generic Java for the IDE
  (`Opt<Integer> a = Opt.some(5)` resolves to the generic factory there) and compiles to `OptInt`. Builds delegated to
  Gradle are correct; only the IDE's own quick-compile would differ, and it does not run the processor's rewrites anyway.
* `Opt<int>` is red in an IDE without a plugin. `specialize-idea/` is that plugin for IntelliJ IDEA, built the way
  the Lombok plugin is built: a `PsiAugmentProvider` (`BridgeAugmentProvider`) adds the bridge overloads
  (`static OptInt some(int)`) as light methods to every `@Specialize` class once the generated class exists in the
  project, so completion, navigation and `OptInt a = Opt.some(5)` resolve — this is exactly how IntelliJ "sees"
  Lombok's generated constructors and getters; and a `HighlightInfoFilter` (`PrimitiveArgumentErrorFilter`) drops
  the "type argument cannot be of primitive type" error on `Opt<int>` when `Opt` is a template. It is a standalone
  Gradle build (the IntelliJ Platform SDK is large): `cd specialize-idea && ./gradlew buildPlugin` produces
  `build/distributions/specialize-idea.zip`, installed via *Settings → Plugins → Install from disk*. The plugin is
  generic: it reads `@Specialize` on any class (including `standsFor` aliases, so `List<int>` is clean too), not
  a list of known templates. `./gradlew updatePluginsXml -PpluginRepositoryUrl=https://nexus.example.com/idea`
  also writes `updatePlugins.xml`; put it next to the zip on any HTTP server and add that URL under
  *Settings → Plugins → ⚙ → Manage Plugin Repositories*: a private plugin repository, updates included, nothing on
  the JetBrains Marketplace. `./gradlew test` there runs fixture tests (`LightJavaCodeInsightFixtureTestCase`) that
  assert the bridges are visible on a template and that `Opt<int>` produces no error while unrelated errors stay.
  Not covered by the plugin: bridges of templates with several `@Specialize.Param` parameters (the IDE resolves the
  generic factory instead, which only differs in the result type). Eclipse and VS Code (JDT) would need the
  equivalent of Lombok's javaagent and are not covered.

## `@Specialize` — templates

Put `@Specialize` on a **top-level class, record or interface**. For every type in `types` (default: all eight
primitives; plain reference types such as `String.class` are allowed) a class named by `namePattern` (default
`{Name}{Type}` → `OptInt`, `OptChar`, `OptString`) is generated in the same package. A template with several type
parameters marks the ones to specialize with `@Specialize.Param` (see below); the rest stay generic.

| in the template | in `OptInt` |
|---|---|
| `T` as variable / field / parameter / return / array element type | `int`, `int[]` |
| `T` as a type argument or bound: `List<T>`, `Optional<T>`, `? extends T`, `<R extends T>` | `List<Integer>`, … (boxed) |
| `(T) expr` | `(Integer) expr` (unboxes when assigned to `int`) |
| `Opt<T>`, `Opt<?>`; `new Opt<>(..)`, `new Opt[n]`, `Opt.some(..)` in a self-typed `return` / initializer; `Opt.<T>some(..)` | `OptInt`, `new OptInt(..)`, `new OptInt[n]`, `OptInt.some(..)` |
| `Opt<U>` (another type variable), `Opt.some(f.apply(value))` inside a method returning `Opt<R>` | stays the generic `Opt` |
| `Function<T,R>` / `Function<A,T>` / `Function<T,T>` | `IntFunction<R>` / `ToIntFunction<A>` (`apply`→`applyAsInt`) / `IntUnaryOperator` |
| `Predicate<T>`, `Consumer<T>`, `Supplier<T>`, `UnaryOperator<T>`, `BinaryOperator<T>` (also `? super T`) | `IntPredicate`, `IntConsumer`, `IntSupplier` (`get`→`getAsInt`), `IntUnaryOperator`, `IntBinaryOperator` |
| `static <T> Opt<T> some(T v)` — a static method whose type variable has the class parameter's name | `static OptInt some(int v)` |
| `Prim.zero()`, `Prim.eq(a,b)`, `Prim.hash(a)`, `Prim.str(a)`, `Prim.compare(a,b)`, `Prim.newArray(n)` | `0`, `(a == b)` (`Double.compare(a,b) == 0` for floating point), `Integer.hashCode(a)`, `String.valueOf(a)`, `Integer.compare(a,b)`, `new int[n]` |
| `Prim.isNull(a)`, `Prim.isPrimitive()`, `Prim.type()`, `Prim.box(a)` | `false`, `true`, `int.class`, `Integer.valueOf(a)` |
| `Prim.write(out, a)`, `Prim.read(in)`, `Prim.put(buf, a)`, `Prim.get(buf)`, `Prim.bytes()` | `out.writeInt(a)`, `in.readInt()`, `buf.putInt(a)`, `buf.getInt()`, `Integer.BYTES` |

`Prim` calls are ordinary boxing-free runtime calls in the generic class (`Prim.zero()` is `null`, `Prim.eq` is
`Objects.equals`, the I/O helpers fall back to Java serialization), so the template is a normal, working `Opt<T>`.
Functional-interface mapping exists for `int`/`long`/`double` (plus `BooleanSupplier`); other primitives keep the
boxed interface. `Prim.isPrimitive()` is a compile-time constant, so `if (Prim.isPrimitive()) { … }` branches are dropped.
Hand-written overloads such as `static OptInt some(int v)` inside the template stay there but are dropped from the
generated class when they collide with the specialized generic method.

### `boxedArguments`: what `Opt<Integer>` means

By default (`BoxedArguments.SPECIALIZE`) both spellings select the specialization: `Opt<Integer>` and `Opt<int>` are
`OptInt`, and `@Boxed Opt<Integer>` opts a single use out. This is the IDE-friendly mode: client code written with
`Opt<Integer>` type-checks in IntelliJ/Eclipse as ordinary generics (the API of `OptInt` mirrors the template's), while
javac compiles it to the primitive class. `@Specialize(boxedArguments = KEEP)` reserves the specialization for the
primitive spelling, so `Opt<Integer>` stays the generic class exactly like `Integer[]` next to `int[]` — the strict
distinction, at the price of red squiggles in an IDE without a plugin. Reference specializations (`Opt<User>`,
`Opt<String>` when listed) are always selected; `@Boxed` opts out of those in either mode. Bridges are unaffected:
`Opt.some(5)` is `OptInt` by overload resolution, `Opt.some((Integer) 5)` is the generic class.

### `autoscan`

```java
@Specialize(autoscan = true)                       // or autoscan = true, types = {String.class} for the union
public final class Opt<T> { ... }
```

Instead of listing primitives, let usage decide: every `Opt<Integer>`, `Opt<int>` or `Opt.<long>m()` in the sources
being compiled (including in generated code, e.g. `OptCodecShort` using `Opt<T>` with `T = short`) adds that
primitive; under `KEEP` only the primitive spelling counts. Reference types are
never inferred. Usage only seen in another module keeps the generic class there, so nothing breaks; the template's own
module decides which specializations exist.

### Several type parameters

```java
@Specialize
public final class Dict<@Specialize.Param(types = {int.class, long.class, String.class}) K, V> {
    private K[] keys = Prim.newArray(8);                    // int[] in DictInt<V>
    public static <K, V> Dict<K, V> of(K key, V value) { ... }
    public <R> Dict<K, R> mapValues(Function<V, R> f) { Dict<K, R> out = Dict.<K, R>empty(); ... }   // DictInt<R> out = DictInt.<R>empty();
}

@Specialize
public record Pair<@Specialize.Param(types = {int.class, double.class}) K, @Specialize.Param(types = long.class) V>(K key, V value) { }
```

`@Specialize.Param` on a type parameter says "substitute this one"; unannotated parameters remain type parameters of
the generated class, so `Dict<Integer, String>` (or `Dict<int, String>`) becomes `DictInt<String>`, `Dict.of(1, "x")`
finds the bridge `static <V> DictInt<V> of(int key, V value)`, and `new Dict<>(..)` becomes `new DictInt<>(..)`. Several
annotated parameters produce every combination: `PairIntLong`, `PairDoubleLong`, annotated
`@Specialized(of = Pair.class, types = {int.class, long.class}, generated = true)`; a hand-written explicit
specialization uses the same `types` attribute. Self references must repeat the template's own variables at the
specialized positions (`Dict<K, V>`, `Dict<?, V>`); `Pair<V, K>` in `swap()` would need the swapped combination to
exist. `Prim` helpers are rewritten where the value's variable is known (`Prim.eq(keys[i], key)` → `==`) or named
explicitly (`Prim.<K>zero()`); a functional interface over two different primitives (`Function<K, V>`) stays boxed
because the JDK has no `IntToLongFunction`-style interface for every pair. `@SpecializeWith` and guard bridges are
limited to templates with exactly one specialized parameter; `autoscan` collects whole combinations
(`Cell<int, long, String>` → `CellIntLong<String>`). For reference targets `Prim.newArray(n)` becomes
`new User[n]` and `Prim.type()` becomes `User.class`, so array-backed templates work for `@SpecializeWith` types too.

### `standsFor`: JDK spellings

```java
@Specialize(types = {int.class, long.class, double.class, String.class}, standsFor = {List.class, ArrayList.class})
public final class MyList<T> implements Iterable<T> { … }

List<int> primes = new ArrayList<>();      // MyListInt primes = new MyListInt();
Map<int, String> names = new HashMap<>();  // DictInt<String> names = new DictInt<>();   (Dict standsFor Map, HashMap)
```

A template can stand in for JDK types: only the **primitive spelling** is affected (`List<Integer>` stays the JDK
list, whatever `boxedArguments` says), and it works in every position a template does (fields, parameters, returns,
`List.of(1, 2)` if the template has `of`). The template has to offer the constructors and methods the client calls;
javac reports the rest as ordinary errors. Inside templates `List<T>` is always the JDK list. Libraries announce their
templates through `META-INF/specialize/<name>.java`, so a consumer gets `List<int>` without ever naming `MyList`; two
templates standing for the same type is a compile error.

### Bridges injected into the generic class

For every static factory whose erasure changes when `T` is substituted (`some(T v)`, `ofAll(T[] vs)`,
`write(DataOutput, Opt<T>)`) the processor injects an overload into the *generic* class itself, Lombok style:

```java
public static OptInt    some(int v)    { return OptInt.some(v); }
public static OptLong   some(long v)   { return OptLong.some(v); }
public static OptString some(String v) { return OptString.some(v); }
public static OptUser   some(User v)   { return OptUser.some(v); }      // only if OptUser declares some(User)
```

Java's overload resolution then picks the primitive overload for `Opt.some(5)` in *any* position (arguments,
`List.of(Opt.some(1))`, ternaries) with no call-site analysis. The bridges are part of `Opt.class`, so they also serve
code compiled later against the jar. Because Java widens primitives during overload resolution, a factory also gets a
*guard* for every primitive that is not specialized (`static Opt<Character> some(char v) { return some((Character) v); }`),
so `Opt.some('c')` stays a generic `Opt<Character>` instead of silently widening into `OptInt`. Varargs factories
are not bridged (the overloads would be ambiguous).

### What is rewritten in client code

Before attribution, in every compiled file:

* `Opt<int>` (and `Opt<Integer>` under `SPECIALIZE`), `Opt<User>` in any type position → `OptInt` / `OptUser`,
  including nested (`List<Opt<int>>` → `List<OptInt>`) and arrays;
* expressions that target such a type: the initializer of a variable/field, the right-hand side of an assignment to a
  known variable, `return` values of a method with such a return type, and **arguments of constructors and methods**
  whose unique matching signature (same arity, from source or class file) has such a parameter:
  `Opt.some(x)` → `OptInt.some(x)`, `Opt.empty()` → `OptInt.empty()`, statically imported `empty()` →
  `OptInt.empty()`, `new Opt<>(x)` → `new OptInt(x)`, `new Opt[n]` → `new OptInt[n]`, looking through parentheses,
  casts, `?:`, `switch` expressions and the receiver of a call chain (`Opt.empty().filter(p)`);
* `Opt.<int>some(x)` anywhere → `OptInt.some(x)`;
* `@Boxed Opt<User> x`, `List<@Boxed Opt<User>>`, a `@Boxed` method → this one type use stays the generic class.

`Opt<int>` is not Java: javac rejects a primitive type argument while attributing signatures, which happens *before*
annotation processors run. The jar therefore also contains a javac `Plugin` with `autoStart() = true` (JDK 14+):
javac starts it whenever the jar is on the processor path, no `-Xplugin` needed. Right after parsing it turns every
`X<int>` into `X<@PrimitiveArgument java.lang.Integer>`, which the processor then rewrites like `X<Integer>`. If no
specialization exists for that primitive (`List<int>`, `Opt<short>` with `short` not in `types`, `@Boxed Opt<int>`)
the processor reports an error at that line instead of boxing silently.

Templates and specializations are found by name resolution over the file's imports and package (the same heuristic
Lombok uses), and read from `@Specialize` / `@Specialized` annotations of classes on the class path when the template
lives in another jar.

Design rule that keeps this predictable: **write client code that type-checks against the generic `Opt<T>`**. The
processor only changes the representation. Code that compiles with the generic class (`int v = opt.get()`,
`opt.transform(x -> x * 10)`, `List.of(Opt.some(1))`) compiles to the specialized one, and IDEs understand it even
without the processor.

## `@Specialize` on static methods

```java
public final class Vec {
    @Specialize(types = {int.class, long.class, double.class})
    public static <T> T fold(T[] xs, T zero, BinaryOperator<T> plus) { … }      // + fold(int[], int, IntBinaryOperator) …

    @Specialize(types = int.class)
    public static <T> String describe(Function<T, String> label, List<T> xs) { … } // + describeInt(IntFunction<String>, List<Integer>)
}
```

The same substitution as for a template class, applied to one static generic method: an overload per type is added
next to it (`Prim` helpers, functional interfaces and `T[]` handled as in templates), and overload resolution picks it
for primitive arguments. When no parameter is a bare `T` / `T[]` the erasure would clash, so the overload gets the
`namePattern` name (`describeInt`). Only `types` and `namePattern` apply; the method must be `static` with exactly one
type parameter, outside a template class.

### Pattern matching

Specializations are ordinary final classes, so every Java 21+ pattern works on them, with `Opt<int>` spelled either way:

```java
static String describe(Object o) {
    return switch (o) {
        case Opt<int> v when v.isDefined() -> "int " + (v.get() * 2);   // OptInt v; int get()
        case OptInt v                       -> "no int";
        case OptUser u                      -> "user " + u.get().name(); // the explicit specialization is its own class
        case Opt<?> other                   -> "generic " + other;       // the generic class: Opt<Character>, …
        default                             -> "other";
    };
}

if (o instanceof Payload(int id, String data, Opt<int> value, Opt<String> optional)) {   // record pattern
    return data + ":" + (id + value.getOrElse(() -> 0));                                // OptInt.getOrElse(IntSupplier)
}
```

One consequence worth knowing: `OptInt`, `OptString` and `OptUser` are not subtypes of `Opt`, so a `case Opt<?>` does
not catch them; list the specializations you care about (`specialize-examples/.../PatternMatchingTest`).

## `@Specialized` — explicit specialization for one `T`

```java
@Specialized(of = Opt.class, type = User.class)
public final class OptUser {                       // completely hand-written, any representation you like
    public static OptUser some(User u) { ... }
    public static OptUser empty() { ... }
    public User get() { ... }
    public String nameOrAnonymous() { ... }         // things the template cannot express for this T
}
```

An explicit specialization wins over the template (also over `autoscan`): nothing is generated for that
(template, type) pair, `Opt<User>` becomes `OptUser` and `Opt.some(user)` is bridged to `OptUser.some` if that factory
exists. Generated classes carry the same annotation with `generated = true`. To be discoverable from a jar an explicit
specialization must live in the template's package and follow `namePattern` (`OptUser`); within one compilation any
name and package works. A hand-written class with the conventional name but without `@Specialized` is an error, not a
silent replacement.

## `@SpecializeWith` — specializing a library template for your own type

```java
@SpecializeWith({Opt.class, OptCodec.class})     // Opt lives in a shared library, UserDTO in the application
public record UserDTO(String name, int age) { }
```

generates `OptUserDTO` and `OptCodecUserDTO` in the application's package; `Opt<UserDTO>` in this module and in every
module compiled against it becomes `OptUserDTO`. This works because a library compiled with the processor stores each
template's source as `META-INF/specialize/<template>.java`, which the application's compilation parses again. A
library compiled without the processor has no such resource and the request is rejected with a clear error. Bridges
(`Opt.some(userDto)`) can only be injected while the template itself is compiled, so across modules rely on the
retargeting (`Opt<UserDTO> u = Opt.some(dto)`, arguments, returns) or write `OptUserDTO.some(dto)`.

## Codecs and messages (Kafka-style)

`specialize-examples` has `OptCodec<T>`, a second template that (de)serializes `Opt` values:

```java
@Specialize(types = {int.class, long.class, double.class, boolean.class})
public final class OptCodec<T> {
    public static <T> void write(DataOutput out, Opt<T> opt) throws IOException {
        out.writeBoolean(opt.isDefined());
        if (opt.isDefined()) Prim.write(out, opt.get());          // OptCodecInt: out.writeInt(opt.get())
    }
    public static <T> Opt<T> read(DataInput in) throws IOException {
        return in.readBoolean() ? Opt.some(Prim.<T>read(in)) : Opt.empty();   // OptCodecInt: in.readInt()
    }
    ...
}
```

Templates compose: `Opt<T>` inside `OptCodec` becomes `Opt<int>` in `OptCodecInt` (the specializer marks a
substituted `T` inside another template as primitive), which the client-side rewrite turns into `OptInt`, and `OptCodec.write(out, Opt.some(5))` resolves to the `int` bridge. A message record
`record OrderEvent(long id, Opt<int> qty, Opt<double> discount, Opt<boolean> express)` therefore holds three
primitive-backed fields, and `new OrderEvent(42L, Opt.some(7), Opt.empty(), Opt.some(true))` compiles without any
annotation on the call. Reading needs the type: `OptCodec.<int>read(in)` or `OptCodecInt.read(in)`.

## Seeing what the processor did (`-Aspecialize.dump`)

There is no delombok, but the same view exists: `-Aspecialize.dump=<dir>` makes the processor write every compilation
unit exactly as it left it — bridges injected into templates, `Opt<Integer>` rewritten to `OptInt`, calls inlined,
`@TailRec` bodies as `while (true)` loops, `@Unroll` loops unrolled, `@ConstEval` initializers as literals. The
examples module sets it, so after `./gradlew build` look into `specialize-examples/build/specialize-dump/…/MathX.java`:

```java
public static long gcd(long a, long b) {
    while (true) {
        if (b == 0) { return a; }
        { long a$tail0 = b; long b$tail1 = a % b; a = a$tail0; b = b$tail1; continue; }
    }
}
```

`javap -c` on the class files shows the same thing one level lower (the tests assert on that output).

## JSON: the `specialize-jackson` module

```java
@Specialize(types = {int.class, long.class, double.class})
public final class Opt<T> {
    @JsonCreator public static <T> Opt<T> some(T value) { … }
    @Absent     public static <T> Opt<T> empty()       { … }     // what null and "field missing" become
    @JsonValue  public Object toJson() { return defined ? value : null; }
}
record OrderEvent(long id, int quantity, Opt<int> promoCode, Opt<double> discount) { }
```

The template declares its JSON shape once with ordinary Jackson annotations; `OptInt` inherits them, so
`{"id":42,"quantity":3,"promoCode":420,"discount":0.1}` is what a message looks like on the wire, with boxing only at
the JSON boundary. What Jackson cannot do alone is the other direction: a JSON `null` or a missing field would leave a
`null` reference in an `Opt<int>` component. `dev.specialize:specialize-jackson` fixes that with one `Module`:
every class carrying `@Specialize` / `@Specialized` whose static no-arg factory is marked `@Absent` gets that value for
`null` and for absent fields. Register it with `mapper.registerModule(new SpecializeModule())`,
`findAndRegisterModules()` (it is a service), or as a Spring `@Bean` (the sample does that).

## `@Inline` — moving work to compile time

```java
public final class MathX {
    public static final int KB_SHIFT = 10;
    @Inline public static int  sq(int x)        { return x * x; }
    @Inline public static long kb(long n)       { return n << KB_SHIFT; }
    @Inline public static int  clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    @Inline public static int  sumOfSquares(int a, int b)   { return sq(a) + sq(b); }   // nested inlining
}
```

Every call site is replaced by the body, parenthesised, with arguments cast to the parameter types and the result to a
primitive return type, so `MathX.half(5)` is `(double) 5 / 2 == 2.5`, not `2`. javac then folds constants:
`static final int C = MathX.sq(3) + MathX.sq(4)` gets `ConstantValue 25`, `MathX.kb(4)` is `ldc2_w 4096L`.

Bodies may be a single expression or a whole block. A block such as `{ int y = x + 1; return y * 2; }` is inlined as a
`switch (0) { default -> { int y$inl1 = x + 1; yield y$inl1 * 2; } }` expression: statements run exactly once, in order, in
their own scope (locals are renamed so they cannot collide with the caller's), and javac compiles it to straight-line
code; a `void` block replaces the call statement as a block. Early `return`s become `yield`s; a `void` block must not
contain `return`.

Rules, enforced with compile errors: the method is `static`, not varargs; a non-void block ends with `return expr;`;
no lambdas / anonymous classes; free names must be parameters, method type variables,
`public` static members of the declaring class, statically imported members or resolvable types (they are stored fully
qualified). Only calls whose arguments are all pure and total (names, literals, and non-throwing operators over those)
are inlined; anything else (a call, `i++`, `new …`, `a[i]`, `a / b`) could otherwise be evaluated twice, not at all,
or in a different order, so such calls are kept. A call is also kept when the declaring class has another method of
the same name and arity, because without types the processor cannot tell which overload javac would pick. Overloads that differ only in parameter types and calls with explicit type arguments are not inlined.
The processor stamps a hidden `@InlineBody(params, paramTypes, returnType, body)` on the method, so consumers compiling
against the class file inline it too; a corrupt `@InlineBody` is reported as a warning and the call is kept.

### Lambdas as arguments

```java
@Inline public static int sumOf(int[] xs, IntUnaryOperator f) { int s = 0; for (int i = 0; i < xs.length; i++) { s += f.applyAsInt(xs[i]); } return s; }

int total = sumOf(data, x -> x * 2);
// →  switch (0) { default -> { int s$inl7 = 0; for (…) { s$inl7 += switch (0) { default -> { var x$l3 = xs[i]; yield x$l3 * 2; } }; } yield s$inl7; } }
```

A lambda passed to an `@Inline` method is applied where the body calls it (`f.applyAsInt(v)`, `c.accept(v)`,
`p.test(v)` …): expression lambdas become the expression with the parameter bound (directly when the argument is pure,
through a temporary otherwise), block lambdas become blocks with their locals renamed and `return`s turned into
`yield`s; in statement position (`c.accept(x);`) the lambda's statements are inlined as a block. Nothing is left
to allocate or call: the loop over `data` is a loop over `data`. Where a lambda cannot be applied (a `return;` in a
void block, an expression that is not a statement in statement position, a block not ending in `return`), it stays
a lambda object cast to the parameter type, which is why lambda arguments are only accepted for parameters with a
concrete declared type (`IntUnaryOperator`, not `UnaryOperator<T>`).

## `@TailRec` — Scala's `@tailrec`

```java
@TailRec
public static long gcd(long a, long b) {
    if (b == 0) { return a; }
    return gcd(b, a % b);                 // compiled as: assign (b, a % b) to (a, b) through temporaries, loop
}
```

The method must be `static`, `private` or `final` (or in a `final` class), so the call is really a call to itself,
and every recursive call must be in tail position: `return f(..)`, either branch of `return c ? f(..) : v`, the tail of
an `if`/`else`, block or `switch` statement, or the last statement of a `void` body. The processor wraps the body in
`while (true)` and turns each tail call into a simultaneous parameter reassignment plus `continue`, so a million-deep
recursion runs on one stack frame. Everything Scala refuses is a compile error here too, reported at the offending
call: a recursive call outside tail position (including inside a lambda, `try` or `synchronized`), a non-recursive
method, a `final` parameter, an overload of the same arity.

## `@Unroll` — counted loops with literal bounds

```java
@Unroll
public static long fnv1a(byte[] block) {
    long hash = FNV_OFFSET;
    for (int i = 0; i < 8; i++) { hash = (hash ^ (block[i] & 0xFF)) * 0x100000001b3L; }   // eight copies, i = 0 … 7
    return hash;
}
```

Inside an `@Unroll` method every `for` loop of the shape `int i = literal; i < | <= | > | >= | != literal; i++ | i-- | i += literal | i -= literal`
is replaced by one copy of the body per iteration with `i` substituted by its value (each copy in its own block, so
locals do not clash; inner loops are unrolled first). Array indices, shifts and `@Inline` arguments then become
constants, and javac folds what it can. Loops of any other shape stay loops. Compile errors: a body that assigns,
increments or redeclares the loop variable; a `break` or `continue` that targets the loop (an unlabeled one inside a
nested loop or `switch` is fine); more than `max` iterations (default 64, `@Unroll(max = 256)`); an `@Unroll` method
without any such loop.

## `@ConstEval` — compile-time evaluation of `static final` initializers

```java
@ConstEval public static final int[]  CRC_TABLE = crcTable(0xEDB88320);   // compiled as new int[]{0, 1996959894, …}
@ConstEval public static final long   MASK      = mask(40);              // ConstantValue 1099511627775: inlined at every use
@ConstEval public static final String BANNER    = "crc32/" + Integer.toHexString(0xEDB88320);
```

The processor compiles the declaring source file on the side (`-proc:none`, through the build's own file manager, so
the class path is exactly the compilation's; for a conventional `src/…/pkg/File.java` layout the source root is added
as `-sourcepath`, so siblings of the same module are found too), loads the class, runs its static initialization once
and reads the field; the initializer is replaced by the literal, and a primitive or `String` field thereby becomes a
*constant variable* that javac inlines. Supported values: primitives and boxes, `String`, enums, `null`, and arrays
(nested) of those; `NaN`/infinities become `Double.NaN` etc. Errors are reported at the field: not `static final`, no
initializer, a field of a local or anonymous class, the side compilation failing (its diagnostics are attached), the
initializer throwing, or a value of another type. The side compilation cannot see the processor's own rewrites, so a
file that needs them (`Opt<int>` in signatures) cannot host `@ConstEval` fields; keep such tables in their own class.

### `Const.eval(expr)`: the same for an expression

```java
public static long checksumSeed() {
    return Const.eval(Long.parseUnsignedLong("14695981039346656037") ^ crcTable(0xEDB88320)[255]);   // ldc2_w <literal>
}
```

`Const.eval` marks an expression anywhere in a method body. The processor splices it into the source as a hidden
static field of the enclosing class (on the line of the opening brace, so diagnostics keep their line numbers),
compiles the unit on the side, and replaces the call with the literal (same value rules as `@ConstEval`); without
the processor `Const.eval` is the identity function. The expression is compiled outside the method, so it can use
static members and constants but not locals or parameters (reported as the side compilation's "cannot find symbol"),
and it must sit in a named class or interface, not in an enum, a local or an anonymous class.

## Benchmarks (`benchmarks/`, JMH)

A standalone build against the published artifacts (`./gradlew publishToMavenLocal` first, then
`cd benchmarks && ./gradlew jmh`). One quick run on a laptop (JDK 25, 1 fork, 5 × 1 s), so read the direction, not
the digits:

| benchmark | what | result |
|---|---|---|
| `DictBenchmark` | 10 000 lookups by `int` key | `DictInt<String>` 31 µs vs `HashMap<Integer, String>` 75 µs |
| `OptBenchmark` | summing 1 000 000 optionals with `getOrElse` | `OptInt` 3.2 ms vs generic `@Boxed Opt<Integer>` 4.0 ms vs `Optional<Integer>` 3.7 ms |
| `InlineBenchmark` | `Vec.sumOf(data, x -> x * 2)` over 100 000 ints | inlined lambda 6.8 µs vs the same loop through `IntUnaryOperator` 7.2 µs |

What this says: primitive-keyed and primitive-valued structures win clearly where the JDK would box on every
access (the map), the optional wins moderately once the values are already in memory (the boxed run also pays for
the boxes at setup, which JMH does not measure), and lambda inlining changes nothing for a monomorphic call the JIT
inlines anyway; its value is in bytecode size, cold code and megamorphic sites, not in this loop. The setup arrays
are where the memory difference lives: a million `OptInt` is a million 16-byte objects with an `int` inside, a
million `Opt<Integer>` is that plus a million `Integer`s.

## Build integration notes

**Gradle incremental compilation.** The processor is deliberately *not* declared incremental. Gradle's model
(`isolating` / `aggregating`) assumes a processor only reacts to annotated elements and that everything it needs to
see carries a `CLASS`- or `RUNTIME`-retained annotation; this processor rewrites files that carry no annotation at all
(`Opt<int>` fields, `List<int>` locals, `Const.eval(…)` calls, callers of `@Inline` methods), and declaring it
`aggregating` only makes Gradle print "Full recompilation is required because '@Override' has source retention" and
fall back anyway. So a module that uses the processor recompiles as a whole; compile avoidance *between* modules is
unaffected (a downstream module recompiles only when the upstream ABI changes). Inlined bodies and evaluated constants
are copied into the caller's class file, so, exactly like javac's own constant inlining, a change to the inline method
needs the caller recompiled; a full module compile guarantees that.

## Limitations (honest list)

* `Opt<int>` relies on the auto-started javac plugin and is red in IDEs (see *IDE support*); Eclipse and VS Code
  (JDT) do not run javac plugins or javac-internal processors at all, exactly like Lombok without its agent.
* Argument retargeting needs a unique callee signature of that arity. Where it cannot decide (overloads such as
  `List.of(E...)`, receivers of unknown type, generic parameters) write `Opt.<int>empty()` or `OptInt.empty()`.
  `Opt.some(x)` never needs it thanks to the bridges; `new Opt<>(x)` in such positions does (constructors cannot be bridged).
* An instance method declaring its own `<T>` inside a template shadows the class type variable and is copied as is;
  a static `<T>` is treated as the class type variable (statics cannot refer to it otherwise).
* A reference-type specialization (`String.class` in `types`) makes `Opt.some("x")` always return `OptString` (a
  non-generic overload is more specific); pass `(Object) "x"` to get the generic `Opt`.
* `map`-like methods returning `Opt<R>` return the *generic* class; `OptInt` and `Opt<Integer>` are distinct classes and
  are not `equals`. Use `transform(UnaryOperator<T>)` to stay specialized.
* Templates must be top-level; nested classes inside a template are copied verbatim (marked
  `@GeneratedSpecialization` too) and cannot appear in bridged signatures. With several type parameters, guard bridges
  and `@SpecializeWith` need exactly one specialized parameter, and a self reference with permuted variables
  (`Pair<V, K>`) requires that combination to be generated. Record templates keep their compact constructor; javac regenerates the
  implicit members (canonical constructor, accessors, `equals`/`hashCode`/`toString`) for the copy.
* Name resolution is import-based, not attributed: a local class shadowing an imported template name would confuse it.
  Retargeting of `Opt.empty()` knows the callee's own and inherited-by-source methods, `this(..)`, `this.f = ..` and
  `f[i] = ..`; a receiver of unknown type (`repo.find().orElse(Opt.empty())`) is left alone.
* `Prim` helpers are for values of type `T`; on a field or variable declared with another type they are left as
  ordinary calls, on an arbitrary expression the processor assumes `T`.

## Opaque types (`@Opaque`) — analysis, not shipped

The natural next step is a Scala-3-like `@Opaque record UserId(long value) { boolean isSystem() {...} }` erased to
`long` at compile time, with `value()` unwrapping. It is not implemented, because an annotation processor cannot make
it *sound*:

* Erasing `UserId → long` before attribution (the only point where a processor can rewrite trees) removes exactly what
  an opaque type is for: javac would then accept a plain `long`, or a `Seconds`, wherever a `UserId` is expected. What is
  left is a zero-cost typedef with namespaced methods.
* Erasing *after* attribution (a `TaskListener` on `ANALYZE`) keeps type checking, but the class files then expose `long`
  in every signature. A consumer compiling against that jar sees `long parse(String)` and fails on
  `UserId id = UserId.parse("1")` — the type information is gone from the binary. Scala solves this because the compiler
  owns the signature metadata (TASTy); javac's symbols come from descriptors.

What would work: a build-level double compilation (type-check against unerased API jars, then compile erased), or
Valhalla value classes (JEP 401) once they leave preview. A *typedef-style* `@Opaque` (zero cost, namespaced methods,
`value()` unwrapping, but no type distinction from the underlying primitive) fits this processor's rewriting model and
can be added if that trade-off is acceptable.
