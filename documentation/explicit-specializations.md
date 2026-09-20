# Explicit specializations

## `@Specialized`: writing one yourself

```java
@Specialized(of = Box.class, type = User.class)
public final class BoxUser {
    public static BoxUser of(User u) { … }
    public User get() { … }
    public String nameOrAnonymous() { … }     // whatever this type needs
}
```

The representation is yours. An explicit specialization wins over the template and over `autoscan`:
nothing is generated for that pair, `Box<User>` becomes `BoxUser`, and `Box.of(user)` is bridged to
`BoxUser.of` when that factory exists.

To be discoverable from a jar it must live in the template's package and follow `namePattern`; within one
compilation any name and package works. A class with the conventional name but no `@Specialized` is an
error rather than a silent replacement. Generated classes carry the same annotation with
`generated = true`.

## `@SpecializeWith`: a library template for your type

```java
@SpecializeWith({Box.class, Codec.class})     // Box and Codec live in a shared library
public record UserDTO(String name, int age) { }
```

generates `BoxUserDTO` and `CodecUserDTO` in the application's package. `Box<UserDTO>` becomes
`BoxUserDTO` in this module and in every module compiled against it.

This works because a library compiled with the processor stores each template's source as
`META-INF/specialize/<template>.java`, which this compilation parses again. A library compiled without
the processor has no such resource, and the request is rejected with an error saying so.

Bridges are injected only while the template itself is compiled, so across modules rely on retargeting
(`Box<UserDTO> u = Box.of(dto)`) or write `BoxUserDTO.of(dto)`.

Templates with several specialized parameters are not supported here: `@SpecializeWith` needs exactly one.
