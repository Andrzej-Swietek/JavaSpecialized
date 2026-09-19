package dev.specialize.processor.registry;

import dev.specialize.processor.model.BinaryTemplate;
import dev.specialize.processor.model.ExplicitSpecialization;
import dev.specialize.processor.model.GeneratedSpecialization;
import dev.specialize.processor.model.SourceTemplate;
import dev.specialize.processor.model.Specialization;
import dev.specialize.processor.model.TargetTuple;
import dev.specialize.processor.model.TargetType;
import dev.specialize.processor.model.Template;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.resolve.TreeUtil;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.processing.Messager;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/** Every template and specialization seen so far, from this compilation or the class path. */
public final class SpecRegistry {
    private final Elements elements;
    private final AnnotationReader reader;
    private final TemplateSources sources;
    private final Aliases aliases = new Aliases();
    private final SpecializationIndex index;
    private final Map<String, Optional<Template>> templates = new LinkedHashMap<>();
    private final Map<String, Boolean> existsCache = new HashMap<>();

    public SpecRegistry(Elements elements, Messager messager, TemplateSources sources) {
        this.elements = elements;
        this.reader = new AnnotationReader(new AnnotationValues(elements), messager);
        this.sources = sources;
        this.index = new SpecializationIndex(elements, reader, this::template, this::typeExists);
    }

    public AnnotationReader reader() {
        return reader;
    }

    public boolean typeExists(String qualified) {
        return existsCache.computeIfAbsent(qualified, q -> elements.getTypeElement(q) != null);
    }

    public boolean typeExistsOrWillBeGenerated(String qualified) {
        return typeExists(qualified) || index.willBeGenerated(qualified);
    }

    public void forget(String qualified) {
        existsCache.remove(qualified);
    }

    public Optional<SourceTemplate> registerTemplate(TypeElement element, JCClassDecl tree, JCCompilationUnit unit) {
        if (!reader.isTopLevel(element)) {
            return reader.reject(element, "@Specialize: only top-level classes can be specialized; move " + element.getSimpleName() + " to its own file");
        }
        Optional<SourceTemplate> template = declaration(element).map(d -> SourceTemplate.compiledHere(d, tree, unit));
        template.ifPresent(t -> {
            templates.put(t.qualified(), Optional.of(t));
            t.tuples().forEach(tuple -> specializationFor(t, tuple));
        });
        return template;
    }

    /**
     * The template a class name denotes, if that class exists and carries {@code @Specialize} (with its source when the
     * library stored it), or the template that {@code standsFor} the name ({@code java.util.List} → {@code MyList}).
     */
    public Optional<Template> template(String qualified) {
        return templates.computeIfAbsent(qualified, q -> Optional.ofNullable(elements.getTypeElement(q))
                        .flatMap(element -> declaration(element).map(d -> sources.load(d).<Template>map(s -> s).orElse(d))))
                .or(() -> aliases.templateOf(qualified).flatMap(this::template));
    }

    private Optional<BinaryTemplate> declaration(TypeElement element) {
        return reader.declaration(element).flatMap(d -> aliases.claim(element, d, reader));
    }

    /** Whether a name is a JDK type some template stands for, as opposed to a template itself. */
    public boolean isAlias(String qualified) {
        return aliases.contains(qualified);
    }

    /** A template proper: inside templates {@code List<T>} stays the JDK list, aliases only act on client spellings. */
    public boolean isTemplate(String qualified) {
        return !isAlias(qualified) && template(qualified).isPresent();
    }

    /** Whether a type tree, resolved in {@code resolver}'s unit, names a template proper. */
    public Predicate<JCTree> templateRef(NameResolver resolver) {
        return clazz -> resolver.resolveTypeName(clazz).filter(this::isTemplate).isPresent();
    }

    /** The template a usage of {@code qualified} (a template or one of its aliases) refers to. */
    public Optional<String> templateNameFor(String qualified) {
        return template(qualified).map(Template::qualified);
    }

    /** The template compiled here that autoscan may grow for a usage of {@code qualified} (a template or an alias). */
    public Optional<SourceTemplate> autoscanned(String qualified) {
        return template(qualified).filter(SourceTemplate.class::isInstance).map(SourceTemplate.class::cast)
                .filter(source -> source.compiledHere() && source.autoscan());
    }

    /** Loading a library's templates registers their aliases before any usage is looked at. */
    public void discover(List<String> templateNames) {
        templateNames.forEach(this::template);
    }

    /** {@code @SpecializeWith(Opt.class)} on {@code holder}: generate {@code OptHolder} next to it, unless a specialization exists already. */
    public void registerRequest(TypeElement holder) {
        TargetTuple tuple = TargetTuple.of(TargetType.reference(holder.getQualifiedName().toString()));
        reader.annotations().withDefaults(AnnotationValues.mirror(holder, Annotations.SPECIALIZE_WITH).orElseThrow())
                .list("value").forEach(templateType -> request(holder, templateType, tuple));
    }

    private void request(TypeElement holder, Object templateType, TargetTuple tuple) {
        Optional<Template> template = AnnotationValues.typeValue(templateType).flatMap(AnnotationValues::targetOf).flatMap(t -> template(t.key()));
        if (template.isEmpty()) {
            reader.reject(holder, "@SpecializeWith: " + templateType + " is not a @Specialize template");
        } else if (!(template.get() instanceof SourceTemplate source)) {
            reader.reject(holder, "@SpecializeWith: the source of " + templateType + " is not available; compile its library with the specialize processor");
        } else if (source.specialized().size() != 1) {
            reader.reject(holder, "@SpecializeWith: " + templateType + " must specialize exactly one type parameter");
        } else if (specializationFor(source, tuple).isEmpty()) {
            String simpleName = TreeUtil.simpleName(source.conventionalName(tuple));
            String pkg = TreeUtil.packageOf(tuple.single().key());
            GeneratedSpecialization spec = new GeneratedSpecialization(pkg.isEmpty() ? simpleName : pkg + "." + simpleName, source, tuple);
            source.request(spec);
            index.record(spec);
        }
    }

    public List<SourceTemplate> sourceTemplates() {
        return templates.values().stream().flatMap(Optional::stream)
                .filter(SourceTemplate.class::isInstance).map(SourceTemplate.class::cast).toList();
    }

    /** Autoscan: {@code template<targets>} appears in this compilation. Only primitives are inferred. */
    public void addScannedType(SourceTemplate template, TargetTuple tuple) {
        if (tuple.allPrimitive() && template.addScanned(tuple)) {
            index.forgetResolution(template, tuple);
            specializationFor(template, tuple);
        }
    }

    public void registerSpecialized(TypeElement element, JCClassDecl tree) {
        reader.specialization(element, Optional.of(tree), this::template).ifPresent(index::recordExplicit);
    }

    /** Explicit first, then the conventionally named class: generated by this run, or found on the class path. */
    public Optional<Specialization> specializationFor(Template template, TargetTuple tuple) {
        return index.specializationFor(template, tuple);
    }

    /** Any specialization by class name: registered, generated by this run, or annotated on the class path. */
    public Optional<Specialization> specByName(String qualified) {
        return index.specByName(qualified);
    }

    public List<ExplicitSpecialization> explicitSpecializationsOf(Template template) {
        return index.explicitSpecializationsOf(template);
    }
}
