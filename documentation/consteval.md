# `@ConstEval` and `Const.eval`

## `@ConstEval` on a `static final` field

```java
@ConstEval public static final int[]  CRC_TABLE = crcTable(0xEDB88320);   // new int[]{0, 1996959894, …}
@ConstEval public static final long   MASK      = mask(40);               // ConstantValue 1099511627775
@ConstEval public static final String BANNER    = "crc32/" + Integer.toHexString(0xEDB88320);
```

The processor compiles the declaring file on the side with `-proc:none`, through the build's own file
manager so the class path matches the compilation, loads the class, runs its static initialization once and
reads the field. The initializer is replaced by the literal. A primitive or `String` field thereby becomes a
constant variable, which javac inlines at every use.

Values: primitives and their boxes, `String`, enums, `null`, and arrays of those, nested. `NaN` and the
infinities become `Double.NaN` and friends.

Errors are reported at the field: not `static final`, no initializer, a field of a local or anonymous class,
a failing side compilation with its diagnostics attached, an initializer that throws, or a value of another
type.

The side compilation does not see the processor's own rewrites, so a file whose signatures need them cannot
host `@ConstEval` fields. Keep such tables in their own class.

## `Const.eval` on an expression

```java
public static long checksumSeed() {
    return Const.eval(Long.parseUnsignedLong("14695981039346656037") ^ crcTable(0xEDB88320)[255]);
}
```

The expression is spliced into the source as a hidden static field of the enclosing class, on the line of
the opening brace so diagnostics keep their line numbers, compiled on the side, and the call is replaced by
the literal. Value rules are the same as for `@ConstEval`.

Because the expression is compiled outside the method, it can use static members and constants but not
locals or parameters, which the side compilation reports as "cannot find symbol". It must sit in a named
class or interface, not in an enum, a local or an anonymous class.

Without the processor on the path, `Const.eval` is the identity function.
