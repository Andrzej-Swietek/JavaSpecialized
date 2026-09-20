# Jackson

`dev.specialize:specialize-jackson` maps a JSON `null` and a missing field to the value a specialization
calls absent, instead of leaving a `null` reference in a primitive-backed component.

The example below is `Opt<T>`, the `Optional`-like template of the examples module.

```java
@Specialize(types = {int.class, long.class, double.class})
public final class Opt<T> {
    @JsonCreator public static <T> Opt<T> some(T value) { … }
    @Absent     public static <T> Opt<T> empty()       { … }
    @JsonValue  public Object toJson() { return defined ? value : null; }
}

record OrderEvent(long id, int quantity, Opt<int> promoCode, Opt<double> discount) { }
```

The template declares its JSON shape once with ordinary Jackson annotations and the specializations
inherit them, so the record above reads and writes
`{"id":42,"quantity":3,"promoCode":420,"discount":0.1}` with boxing only at the JSON boundary.

Register the module:

```java
new ObjectMapper().registerModule(new SpecializeModule());
new ObjectMapper().findAndRegisterModules();      // it is a service
```

or as a Spring `@Bean`, which `samples/spring-boot-app` does.

The module is not tied to any particular template: it applies to every class carrying `@Specialize` or
`@Specialized` whose static no-argument factory is marked `@Absent`. Two such factories in one class is an
error naming both.
