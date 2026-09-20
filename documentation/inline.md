# `@Inline`

Each call is replaced by the method's body, parenthesised, with arguments cast to the parameter types and
the result to a primitive return type. javac then folds what it can.

```java
public final class MathX {
    public static final int KB_SHIFT = 10;
    @Inline public static int  sq(int x)  { return x * x; }
    @Inline public static long kb(long n) { return n << KB_SHIFT; }
    @Inline public static int  sumOfSquares(int a, int b) { return sq(a) + sq(b); }   // nested
}

static final int C = MathX.sq(3) + MathX.sq(4);   // ConstantValue 25 in the class file
MathX.kb(4)                                        // ldc2_w 4096L
```

The cast matters: `MathX.half(5)` with a `double` return is `(double) 5 / 2`, so `2.5` rather than `2`.

## Bodies

A body is a single expression or a block. A block is inlined as a `switch` expression, so its statements
run once, in order, in their own scope, and its locals are renamed apart from the caller's:

```java
@Inline public static int twice(int x) { int y = x + 1; return y * 2; }

twice(n)   // switch (0) { default -> { int y$inl1 = n + 1; yield y$inl1 * 2; } }
```

Early `return`s become `yield`s. A `void` block replaces the call statement as a block and must not
contain `return`.

## Lambda arguments

```java
@Inline public static int sumOf(int[] xs, IntUnaryOperator f) {
    int s = 0;
    for (int i = 0; i < xs.length; i++) { s += f.applyAsInt(xs[i]); }
    return s;
}

int total = sumOf(data, x -> x * 2);     // the loop over data, with x * 2 in the body; nothing allocated
```

A lambda passed to an inline method is applied where the body calls it. Expression lambdas become the
expression with the parameter bound, directly for a pure argument and through a temporary otherwise; block
lambdas become blocks with their locals renamed. In statement position the lambda's statements are inlined
as a block.

Where it cannot be applied — a `return;` in a void block, a body not ending in `return`, an expression that
is not a statement in statement position — the lambda stays an object cast to the parameter type. Lambda
arguments are therefore accepted only for parameters with a concrete declared type, such as
`IntUnaryOperator` rather than `UnaryOperator<T>`.

## Rules

Compile errors: the method is not `static`, it is varargs, a non-void block does not end in `return expr;`,
the body contains a lambda or an anonymous class, or a free name is none of a parameter, a method type
variable, a `public` static member of the declaring class, a statically imported member or a resolvable
type. Bodies are stored fully qualified.

Calls that are kept instead of inlined:

- an argument that is not pure and total — a call, `i++`, `new …`, `a[i]`, `a / b` — could otherwise be
  evaluated twice, never, or out of order
- the declaring class has another method of the same name and arity, so the overload javac would pick is
  not decidable without types
- the call has explicit type arguments

## Across modules

The processor stamps `@InlineBody(params, paramTypes, returnType, body)` on the compiled method, so a
consumer compiling against the class file inlines it too. A corrupt `@InlineBody` is a warning and the call
is kept.
