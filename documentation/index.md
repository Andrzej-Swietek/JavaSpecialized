# specialize

A javac annotation processor for Java 25 that specializes generic code for primitives and moves work
from run time to compile time.

## The model

A **template** is a class carrying `@Specialize`. For every type in `types` the processor generates a
**specialization**: a copy of the class with the type parameter replaced, named `{Name}{Type}`.
`Box<int>` in any other file is rewritten to `BoxInt` before javac attributes types.

```java
@Specialize(types = int.class) public final class Box<T> { … }   // template
                                                                 // BoxInt   generated specialization
@Specialized(of = Box.class, type = User.class) class BoxUser { … }  // explicit specialization, hand-written
```

Everything else the processor does is independent of that model: `@Inline`, `@TailRec`, `@Unroll`,
`@ConstEval` work in any class.

## Pages

- [Getting started](getting-started.md) — install, requirements, seeing the output
- [Templates](templates.md) — `@Specialize` and what a specialization replaces
- [Call sites](call-sites.md) — what changes in code that uses a template
- [Explicit specializations](explicit-specializations.md) — `@Specialized`, `@SpecializeWith`
- [Methods](methods.md) — `@Specialize` on a static generic method
- [`Prim`](prim.md) — writing a template that works both generic and specialized
- [`@Inline`](inline.md) · [`@TailRec`](tailrec.md) · [`@Unroll`](unroll.md) · [`@ConstEval`](consteval.md)
- [Jackson](jackson.md) · [IDE support](ide.md)
- [Internals](internals.md) · [Limitations](limitations.md) · [Benchmarks](benchmarks.md)
