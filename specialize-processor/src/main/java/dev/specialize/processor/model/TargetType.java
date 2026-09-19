package dev.specialize.processor.model;

import dev.specialize.processor.resolve.NameResolver;

import com.sun.tools.javac.tree.JCTree.JCExpression;
import java.util.Optional;

/** A type a template is specialized for. */
public sealed interface TargetType permits PrimitiveTarget, ReferenceTarget {

    /** {@code int} for primitives, the fully qualified name for reference types. */
    String key();

    /** Fully qualified box for primitives, {@link #key()} for reference types. */
    String boxed();

    /** Capitalized simple name used in generated class names: {@code Int}, {@code Char}, {@code User}. */
    String suffix();

    static TargetType primitive(String name) {
        return PrimitiveTarget.of(name);
    }

    static TargetType reference(String qualifiedName) {
        return new ReferenceTarget(qualifiedName);
    }

    /** {@code java.lang.Integer} → the {@code int} target, any other name → a reference target. */
    static TargetType ofQualified(String qualifiedName) {
        return PrimitiveTarget.ofBox(qualifiedName).map(TargetType.class::cast).orElseGet(() -> reference(qualifiedName));
    }

    /**
     * {@code Integer} or {@code User} written as a type argument ({@code int} was already turned into a marked
     * {@code Integer} by the plugin); empty for wildcards, type variables and nested generics.
     */
    static Optional<TargetType> classify(NameResolver resolver, JCExpression typeArgument) {
        return resolver.resolveTypeName(typeArgument).map(TargetType::ofQualified);
    }
}
