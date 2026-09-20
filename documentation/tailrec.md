# `@TailRec`

```java
@TailRec
public static long gcd(long a, long b) {
    if (b == 0) { return a; }
    return gcd(b, a % b);
}
```

The body is wrapped in `while (true)` and each tail call becomes a simultaneous reassignment of the
parameters through temporaries, followed by `continue`. A million-deep recursion then runs in one stack
frame.

```java
public static long gcd(long a, long b) {
    while (true) {
        if (b == 0) { return a; }
        { long a$tail0 = b; long b$tail1 = a % b; a = a$tail0; b = b$tail1; continue; }
    }
}
```

## What is a tail position

`return f(..)`, either branch of `return c ? f(..) : v`, the tail of an `if`/`else`, block or `switch`
statement, and the last statement of a `void` body.

## Errors

Reported at the offending call, with the same rules Scala's `@tailrec` applies:

- the method is not `static`, `private` or `final`, and its class is not `final`, so the call could
  dispatch elsewhere
- a recursive call outside tail position, including inside a lambda, a `try` or a `synchronized` block
- no recursive call at all
- a `final` parameter, which the loop cannot reassign
- an overload with the same arity, which makes the recursive call ambiguous
