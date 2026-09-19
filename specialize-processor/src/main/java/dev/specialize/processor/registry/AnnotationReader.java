package dev.specialize.processor.registry;

import dev.specialize.BoxedArguments;
import dev.specialize.processor.model.BinaryTemplate;
import dev.specialize.processor.model.ExplicitSpecialization;
import dev.specialize.processor.model.GeneratedSpecialization;
import dev.specialize.processor.model.ReferenceTarget;
import dev.specialize.processor.model.Specialization;
import dev.specialize.processor.model.TargetTuple;
import dev.specialize.processor.model.TargetType;
import dev.specialize.processor.model.Template;
import dev.specialize.processor.model.Template.TypeParameter;
import dev.specialize.processor.registry.AnnotationValues.Values;
import dev.specialize.processor.resolve.Annotations;

import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.tools.Diagnostic;

/** Turns {@code @Specialize}, {@code @Specialized} and {@code @SpecializeWith} mirrors into model objects; problems are reported at the element. */
public record AnnotationReader(AnnotationValues annotations, Messager messager) {

    /** A template declaration with the aliases it declares ({@code standsFor}). */
    public record Declaration(BinaryTemplate template, List<String> standsFor) {
    }

    /** What a {@code @Specialize} method asks for. */
    public record MethodSpecialization(String ownerQualified, String typeVariable, List<TargetType> types, String namePattern) {
    }

    public Optional<Declaration> declaration(TypeElement element) {
        return AnnotationValues.mirror(element, Annotations.SPECIALIZE).flatMap(mirror -> declaration(element, mirror));
    }

    private Optional<Declaration> declaration(TypeElement element, AnnotationMirror mirror) {
        Values values = annotations.withDefaults(mirror);
        boolean autoscan = values.flag("autoscan");
        boolean classTypesGiven = AnnotationValues.explicitlySet(mirror).contains("types");
        List<? extends TypeParameterElement> declared = element.getTypeParameters();
        if (declared.isEmpty()) {
            return reject(element, "@Specialize: the template must declare a type parameter");
        }
        if (declared.size() > 1 && classTypesGiven) {
            return reject(element, "@Specialize: with several type parameters give the types on @Specialize.Param, not on @Specialize");
        }
        List<Optional<TypeParameter>> parameters = declared.stream()
                .map(tpe -> parameter(element, tpe, values, autoscan && !classTypesGiven, declared.size() > 1))
                .toList();
        List<Optional<String>> aliases = values.list("standsFor").map(value -> alias(element, value)).toList();
        if (!parameters.stream().allMatch(Optional::isPresent) || !aliases.stream().allMatch(Optional::isPresent)) {
            return Optional.empty();
        }
        List<TypeParameter> typeParameters = parameters.stream().map(Optional::orElseThrow).toList();
        if (typeParameters.stream().noneMatch(TypeParameter::specialized)) {
            return reject(element, "@Specialize: with several type parameters mark the ones to specialize with @Specialize.Param");
        }
        BinaryTemplate template = new BinaryTemplate(element.getQualifiedName().toString(), values.string("namePattern"), typeParameters,
                BinaryTemplate.product(typeParameters), autoscan, values.enumValue("boxedArguments", BoxedArguments.class), Optional.of(element));
        return Optional.of(new Declaration(template, aliases.stream().map(Optional::orElseThrow).toList()));
    }

    /** {@code @Specialize.Param} decides for each parameter; without it, a single parameter takes the class-level types. */
    private Optional<TypeParameter> parameter(TypeElement element, TypeParameterElement tpe, Values classValues, boolean scannedOnly, boolean several) {
        String name = tpe.getSimpleName().toString();
        Optional<AnnotationMirror> param = AnnotationValues.mirror(tpe, Annotations.SPECIALIZE_PARAM);
        if (param.isPresent()) {
            return targets(element, annotations.withDefaults(param.get())).map(types -> TypeParameter.specialized(name, types));
        }
        if (several) {
            return Optional.of(TypeParameter.generic(name));
        }
        return scannedOnly ? Optional.of(TypeParameter.specialized(name, List.of())) : targets(element, classValues).map(types -> TypeParameter.specialized(name, types));
    }

    private Optional<String> alias(TypeElement element, Object value) {
        Optional<String> alias = AnnotationValues.typeValue(value).flatMap(AnnotationValues::targetOf)
                .filter(ReferenceTarget.class::isInstance).map(TargetType::key);
        return alias.or(() -> reject(element, "@Specialize: standsFor must name classes or interfaces, not " + value));
    }

    private Optional<List<TargetType>> targets(TypeElement element, Values values) {
        List<Optional<TargetType>> targets = values.list("types").map(type -> AnnotationValues.typeValue(type).flatMap(AnnotationValues::targetOf)
                .or(() -> reject(element, "@Specialize: cannot specialize for type " + type + "; use a primitive or a non-generic class"))).toList();
        return targets.stream().allMatch(Optional::isPresent) ? Optional.of(targets.stream().map(Optional::orElseThrow).toList()) : Optional.empty();
    }

    /** {@code @Specialize} on a static method: the types to overload it for, with the name pattern. */
    public Optional<MethodSpecialization> specializedMethod(ExecutableElement method) {
        Values values = annotations.withDefaults(AnnotationValues.mirror(method, Annotations.SPECIALIZE).orElseThrow());
        TypeElement owner = (TypeElement) method.getEnclosingElement();
        if (AnnotationValues.mirror(owner, Annotations.SPECIALIZE).isPresent()) {
            return reject(method, "@Specialize: a template's methods are specialized with the class; annotate the class or the method, not both");
        }
        if (!method.getModifiers().contains(Modifier.STATIC) || method.getTypeParameters().size() != 1) {
            return reject(method, "@Specialize: the method must be static and declare exactly one type parameter");
        }
        return targets(owner, values).map(types -> new MethodSpecialization(owner.getQualifiedName().toString(),
                method.getTypeParameters().getFirst().getSimpleName().toString(), types, values.string("namePattern")));
    }

    /** The {@code @Specialized} class {@code element}, with its source when it is compiled in this run. */
    public Optional<Specialization> specialization(TypeElement element, Optional<JCClassDecl> source, Function<String, Optional<Template>> templates) {
        Values values = annotations.withDefaults(AnnotationValues.mirror(element, Annotations.SPECIALIZED).orElseThrow());
        Optional<Template> template = values.target("of").filter(ReferenceTarget.class::isInstance).flatMap(of -> templates.apply(of.key()));
        List<TargetType> targets = Stream.concat(values.target("type").stream(),
                        values.list("types").flatMap(v -> AnnotationValues.typeValue(v).flatMap(AnnotationValues::targetOf).stream()))
                .toList();
        if (template.isEmpty() || targets.isEmpty()) {
            return reject(element, "@Specialized: 'of' must be a @Specialize template and 'type' a primitive or plain class");
        }
        if (targets.size() != template.get().specialized().size()) {
            return reject(element, "@Specialized: " + template.get().simple() + " specializes " + template.get().specialized().size()
                    + " type parameter(s); give exactly that many in 'types'");
        }
        String qualified = element.getQualifiedName().toString();
        TargetTuple tuple = new TargetTuple(targets);
        return Optional.of(values.flag("generated")
                ? new GeneratedSpecialization(qualified, template.get(), tuple)
                : new ExplicitSpecialization(qualified, template.get(), tuple, source));
    }

    public boolean isTopLevel(TypeElement element) {
        return element.getNestingKind() == NestingKind.TOP_LEVEL;
    }

    public <T> Optional<T> reject(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
        return Optional.empty();
    }
}
