package dev.specialize.processor.registry;

import dev.specialize.processor.model.TargetType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** Reads the API annotations through the element API, so class files work as well as sources. */
public record AnnotationValues(Elements elements) {

    /** Every element of one annotation, defaults included, typed by what the annotation declares. */
    public record Values(Map<String, Object> raw) {
        public boolean flag(String name) {
            return Boolean.TRUE.equals(raw.get(name));
        }

        public String string(String name) {
            return (String) raw.get(name);
        }

        public <E extends Enum<E>> E enumValue(String name, Class<E> type) {
            return Enum.valueOf(type, ((VariableElement) raw.get(name)).getSimpleName().toString());
        }

        /** A class-literal element; empty for {@code void.class} and for a class javac could not resolve. */
        public Optional<TargetType> target(String name) {
            return typeValue(raw.get(name)).flatMap(AnnotationValues::targetOf);
        }

        /** The raw values of an array element. */
        public Stream<Object> list(String name) {
            return ((List<?>) raw.get(name)).stream().map(v -> ((AnnotationValue) v).getValue());
        }
    }

    public static Optional<AnnotationMirror> mirror(Element element, String annotationQualifiedName) {
        return element.getAnnotationMirrors().stream()
                .map(AnnotationMirror.class::cast)
                .filter(am -> ((TypeElement) am.getAnnotationType().asElement()).getQualifiedName().contentEquals(annotationQualifiedName))
                .findFirst();
    }

    public Values withDefaults(AnnotationMirror mirror) {
        return new Values(elements.getElementValuesWithDefaults(mirror).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> e.getKey().getSimpleName().toString(), e -> e.getValue().getValue())));
    }

    /** Names of the elements written out explicitly in the source. */
    public static Set<String> explicitlySet(AnnotationMirror mirror) {
        return mirror.getElementValues().keySet().stream().map(k -> k.getSimpleName().toString()).collect(Collectors.toUnmodifiableSet());
    }

    /** A class-literal annotation value; javac hands over a {@code String} for a class it could not resolve. */
    public static Optional<TypeMirror> typeValue(Object value) {
        return value instanceof TypeMirror type ? Optional.of(type) : Optional.empty();
    }

    /** A class literal from an annotation: a primitive or a (never parameterized) class; empty for {@code void.class}. */
    public static Optional<TargetType> targetOf(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return Optional.of(TargetType.primitive(type.getKind().name().toLowerCase(Locale.ROOT)));
        }
        if (type instanceof DeclaredType declared) {
            return Optional.of(TargetType.reference(((TypeElement) declared.asElement()).getQualifiedName().toString()));
        }
        return Optional.empty();
    }
}
