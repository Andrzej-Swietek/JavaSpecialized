package dev.specialize.processor.registry;

import dev.specialize.processor.model.BinaryTemplate;
import dev.specialize.processor.registry.AnnotationReader.Declaration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.TypeElement;

/** The JDK types templates stand for: {@code java.util.List} → the template declaring {@code standsFor = List.class}. */
final class Aliases {
    private final Map<String, String> templateByAlias = new HashMap<>();

    /** The declaration with its aliases claimed; empty when another template already stands for one of them. */
    Optional<BinaryTemplate> claim(TypeElement element, Declaration declaration, AnnotationReader reader) {
        String qualified = element.getQualifiedName().toString();
        boolean allFree = declaration.standsFor().stream().map(alias -> {
            String taken = templateByAlias.getOrDefault(alias, qualified);
            if (!taken.equals(qualified)) {
                return reader.<String>reject(element, "@Specialize: " + alias + " is already taken by " + taken + "; only one template can stand for it");
            }
            templateByAlias.put(alias, qualified);
            return Optional.of(alias);
        }).toList().stream().allMatch(Optional::isPresent);
        return allFree ? Optional.of(declaration.template()) : Optional.empty();
    }

    boolean contains(String qualified) {
        return templateByAlias.containsKey(qualified);
    }

    Optional<String> templateOf(String qualified) {
        return Optional.ofNullable(templateByAlias.get(qualified));
    }
}
