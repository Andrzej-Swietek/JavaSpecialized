# Limitations

- `Box<int>` depends on the auto-started javac plugin. Eclipse and VS Code run neither javac plugins nor
  javac-internal processors, exactly like Lombok without its agent.
- Argument retargeting needs a unique callee signature of that arity. Where overloads such as
  `List.of(E...)`, a receiver of unknown type or a generic parameter make it ambiguous, write
  `Box.<int>empty()` or `BoxInt.empty()`. `Box.of(x)` is covered by the bridges; `new Box<>(x)` is not,
  because constructors cannot be bridged.
- An instance method declaring its own `<T>` shadows the class type variable and is copied unchanged. A
  static `<T>` is treated as the class type variable, since statics cannot refer to it otherwise.
- A reference specialization makes `Box.of("x")` return `BoxString`, because a non-generic overload is more
  specific. Pass `(Object) "x"` for the generic class.
- A `map`-like method returning `Box<R>` returns the generic class. `BoxInt` and `Box<Integer>` are
  different classes and are not `equals`. Use a `UnaryOperator<T>` method to stay specialized.
- Templates are top-level. Nested classes inside one are copied verbatim and cannot appear in bridged
  signatures. Guard bridges and `@SpecializeWith` need exactly one specialized parameter.
- Record templates keep their compact constructor; javac regenerates the canonical constructor, accessors,
  `equals`, `hashCode` and `toString` for the copy.
- Name resolution is import-based rather than attributed, so a local class shadowing an imported template
  name would confuse it.
- Gradle incremental annotation processing is not supported: the processor rewrites files that carry no
  annotation at all, which neither the `isolating` nor the `aggregating` model allows. A module using the
  processor recompiles as a whole; compile avoidance between modules is unaffected.
- Inlined bodies and evaluated constants are copied into the caller's class file, so a change to an
  `@Inline` method needs its callers recompiled, exactly like javac's own constant inlining.

## Opaque types

A Scala-3-like `@Opaque record UserId(long value)` erased to `long` is not implemented, because an
annotation processor cannot make it sound. Erasing before attribution, the only point where a processor
rewrites trees, removes the type distinction that an opaque type exists for. Erasing after attribution
keeps type checking but publishes `long` in every signature, so a consumer compiling against the jar sees
`long parse(String)`. Scala avoids this because the compiler owns the signature metadata; javac's symbols
come from descriptors.

A typedef-style `@Opaque` — zero cost, namespaced methods, `value()` unwrapping, no type distinction from
the underlying primitive — fits this processor's model and could be added.
