package dev.specialize.processor.model;

import dev.specialize.BoxedArguments;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;

/**
 * A template known only through its class file (or the declaration part of a {@link SourceTemplate}).
 */
public record BinaryTemplate(
        String qualified,
        String namePattern,
        List<TypeParameter> parameters,
        List<TargetTuple> tuples,
        boolean autoscan,
        BoxedArguments boxedArguments,
        Optional<TypeElement> element
) implements Template {

    public BinaryTemplate {
        parameters = List.copyOf(parameters);
        tuples = List.copyOf(tuples);
    }

    /**
     * Every combination of the specialized parameters' types, in declaration order.
     */
    public static List<TargetTuple> product(List<TypeParameter> parameters) {
        List<List<TargetType>> rows = List.of(List.of());
        for (TypeParameter parameter : parameters.stream().filter(TypeParameter::specialized).toList()) {
            rows = rows.stream()
                    .flatMap(row -> parameter.types().stream().map(type -> Stream.concat(row.stream(), Stream.of(type)).toList()))
                    .toList();
        }
        return rows.stream().map(TargetTuple::new).toList();
    }
}
