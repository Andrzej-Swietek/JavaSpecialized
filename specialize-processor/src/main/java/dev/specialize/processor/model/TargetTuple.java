package dev.specialize.processor.model;

import java.util.List;
import java.util.stream.Collectors;

/** The types a specialization stands for: one per specialized type parameter of the template, in declaration order. */
public record TargetTuple(List<TargetType> targets) {

    public TargetTuple {
        targets = List.copyOf(targets);
    }

    public static TargetTuple of(TargetType... targets) {
        return new TargetTuple(List.of(targets));
    }

    /** {@code int,java.lang.String} — identity of the tuple. */
    public String key() {
        return targets.stream().map(TargetType::key).collect(Collectors.joining(","));
    }

    /** {@code IntString} — what {@code {Type}} expands to in a generated class name. */
    public String suffix() {
        return targets.stream().map(TargetType::suffix).collect(Collectors.joining());
    }

    public boolean isSingle() {
        return targets.size() == 1;
    }

    public TargetType single() {
        return targets.getFirst();
    }

    public boolean allPrimitive() {
        return targets.stream().allMatch(PrimitiveTarget.class::isInstance);
    }
}
