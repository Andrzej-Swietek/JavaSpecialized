# `Prim`

A template has to compile and run as an ordinary generic class as well as specialized. `Prim` holds the
operations that differ: in the generic class they are plain runtime calls, in a specialization the
processor replaces each call with the primitive expression.

| in the template | in the generic class | in `BoxInt` |
|---|---|---|
| `Prim.zero()` | `null` | `0` |
| `Prim.eq(a, b)` | `Objects.equals` | `a == b`, `Double.compare(a, b) == 0` for floating point |
| `Prim.hash(a)` | `Objects.hashCode` | `Integer.hashCode(a)` |
| `Prim.str(a)` | `String.valueOf` | `String.valueOf(a)` |
| `Prim.compare(a, b)` | `Comparable` | `Integer.compare(a, b)` |
| `Prim.newArray(n)` | `Object[]` | `new int[n]` |
| `Prim.isNull(a)` | `a == null` | `false` |
| `Prim.isPrimitive()`, `Prim.type()` | `false`, `Object.class` | `true`, `int.class` |
| `Prim.box(a)` | `a` | `Integer.valueOf(a)` |
| `Prim.write(out, a)`, `Prim.read(in)` | Java serialization | `out.writeInt(a)`, `in.readInt()` |
| `Prim.put(buf, a)`, `Prim.get(buf)`, `Prim.bytes()` | serialization | `buf.putInt(a)`, `buf.getInt()`, `Integer.BYTES` |

`Prim.isPrimitive()` is a compile-time constant in a specialization, so `if (Prim.isPrimitive())` branches
are dropped with the branch not taken.

For a reference specialization, `Prim.newArray(n)` becomes `new User[n]` and `Prim.type()` becomes
`User.class`, so array-backed templates also work with `@SpecializeWith`.

`Prim` helpers apply to values of type `T`. On a variable declared with another type they are left as
ordinary calls; where the value's type variable is ambiguous, name it: `Prim.<K>zero()`.

The generic fallbacks of `read` and `get` deserialize arbitrary bytes under a size and depth limit, so use
them only on trusted input.
