# `@Unroll`

```java
@Unroll
public static long fnv1a(byte[] block) {
    long hash = FNV_OFFSET;
    for (int i = 0; i < 8; i++) { hash = (hash ^ (block[i] & 0xFF)) * 0x100000001b3L; }
    return hash;
}
```

Inside an `@Unroll` method, every `for` loop of the shape

```
for (int i = <literal>; i < | <= | > | >= | != <literal>; i++ | i-- | i += <literal> | i -= <literal>)
```

is replaced by one copy of its body per iteration, with the variable substituted by its value. Each copy
sits in its own block, so locals do not clash, and inner loops are unrolled first. Array indices, shifts
and `@Inline` arguments then become constants for javac to fold. Loops of any other shape are left alone.

## Errors

- the body assigns, increments or redeclares the loop variable
- a `break` or `continue` targets the unrolled loop; one inside a nested loop or `switch` is fine
- more iterations than `max`, which defaults to 64 and is raised with `@Unroll(max = 256)`
- the method has no loop of that shape
