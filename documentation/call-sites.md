# Call sites

Code that uses a template is rewritten before javac attributes types. The rule that keeps this
predictable: **write code that type-checks against the generic class**. The processor changes the
representation, not the API.

## Types

`Box<int>`, `Box<Integer>` and `Box<User>` become `BoxInt` and `BoxUser` in every type position —
fields, parameters, returns, locals, records, interfaces — including nested (`List<Box<int>>`) and arrays.

`Box<int>` is not legal Java: javac rejects a primitive type argument while attributing signatures, which
happens before annotation processors run. The jar therefore carries a javac `Plugin` with
`autoStart() = true`, which javac starts whenever the jar is on the processor path. Right after parsing it
turns `X<int>` into `X<@PrimitiveArgument Integer>`, which the processor rewrites like `X<Integer>`. A
marker left over because no specialization exists for that primitive is reported as an error at that line.

## Expressions

An expression is retargeted when its target type is known:

- the initializer of a variable or field, and the right-hand side of an assignment to a known variable
- `return` in a method whose return type is a specialization
- arguments of a constructor or method whose unique matching signature declares such a parameter
- `Box.<int>of(x)` anywhere

So `Box.of(x)` becomes `BoxInt.of(x)`, `new Box<>(x)` becomes `new BoxInt(x)`, and a statically imported
`empty()` becomes `BoxInt.empty()`. The rewrite looks through parentheses, casts, `?:`, `switch`
expressions and the receiver of a call chain.

## Bridges

For every static factory whose erasure changes under substitution, an overload is injected into the
generic class itself:

```java
public static BoxInt    of(int v)    { return BoxInt.of(v); }
public static BoxLong   of(long v)   { return BoxLong.of(v); }
public static BoxString of(String v) { return BoxString.of(v); }
```

Overload resolution then picks the primitive overload for `Box.of(5)` in any position, with no call-site
analysis, and the bridges are part of `Box.class`, so code compiled later against the jar gets them too.
Since Java widens primitives during overload resolution, a factory also gets a guard for every primitive
that is not specialized, so `Box.of('c')` stays a generic `Box<Character>` instead of widening into
`BoxInt`. Varargs factories are not bridged, because the overloads would be ambiguous.

## `boxedArguments`: what `Box<Integer>` means

Default `BoxedArguments.SPECIALIZE`: `Box<Integer>` and `Box<int>` both select `BoxInt`. Client code
written with `Box<Integer>` type-checks as ordinary generics in any IDE and compiles to the primitive
class.

`@Specialize(boxedArguments = KEEP)` reserves the specialization for the primitive spelling, so
`Box<Integer>` stays the generic class, the way `Integer[]` differs from `int[]`.

`@Boxed` opts one use out in either mode:

```java
@Boxed Box<Integer> boxed = Box.of((Integer) 5);     // the generic class, both in the type and in the call
```

Reference specializations such as `Box<User>` are always selected; `@Boxed` opts out of those too.

## Pattern matching

Specializations are ordinary final classes, so every Java 21+ pattern works:

```java
switch (o) {
    case Box<int> v  -> "int " + v.get();      // BoxInt v
    case BoxUser u   -> "user " + u.get();
    case Box<?> b    -> "generic " + b;
    default          -> "other";
}
```

`BoxInt` is not a subtype of `Box`, so `case Box<?>` does not match it; list the specializations you care
about.
