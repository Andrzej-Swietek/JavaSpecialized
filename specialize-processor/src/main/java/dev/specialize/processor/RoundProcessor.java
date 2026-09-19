package dev.specialize.processor;

import dev.specialize.processor.consteval.ConstEvalRewriter;
import dev.specialize.processor.consteval.ConstantEvaluator;
import dev.specialize.processor.inline.InlineBodyParser;
import dev.specialize.processor.inline.InlineExpander;
import dev.specialize.processor.inline.InlineMethodFactory;
import dev.specialize.processor.inline.InlineRegistry;
import dev.specialize.processor.model.SourceTemplate;
import dev.specialize.processor.registry.AnnotationValues;
import dev.specialize.processor.registry.ClassIndex;
import dev.specialize.processor.registry.SpecRegistry;
import dev.specialize.processor.registry.TemplateSources;
import dev.specialize.processor.registry.UsageScanner;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.resolve.ClassScope;
import dev.specialize.processor.resolve.CompilationUnitResolver;
import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.rewrite.UseSiteRewriter;
import dev.specialize.processor.specialize.BridgeInjector;
import dev.specialize.processor.specialize.MethodSpecializer;
import dev.specialize.processor.specialize.SpecializationGenerator;
import dev.specialize.processor.tailrec.TailRecursionEliminator;
import dev.specialize.processor.unroll.LoopUnroller;

import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

/** One processing round: register, scan, rewrite (constants, loops, tail calls, inlining, use sites), generate, bridge. */
public final class RoundProcessor {
    private final Javac javac;
    private final TemplateSources sources;
    private final SpecRegistry registry;
    private final InlineRegistry inlines;
    private final ClassIndex classIndex;
    private final SpecializationGenerator generator;
    private final BridgeInjector bridges;
    private final Map<SourceTemplate, NameResolver> templateResolvers = new IdentityHashMap<>();
    private final Optional<SourceDump> dump;

    public RoundProcessor(Javac javac, Optional<SourceDump> dump) {
        this.javac = javac;
        this.dump = dump;
        this.sources = new TemplateSources(javac.filer(), javac.parsers(), javac.messager());
        this.registry = new SpecRegistry(javac.elements(), javac.messager(), sources);
        registry.discover(TemplateSources.publishedNames(javac.fileManager()));
        this.inlines = new InlineRegistry(javac.elements(),
                new InlineMethodFactory(javac.make(), javac.names(), javac.messager(), new InlineBodyParser(javac.parsers(), javac.log())));
        this.classIndex = new ClassIndex(javac.elements(), javac.names(), registry);
        Function<SourceTemplate, UseSiteRewriter> rewriters = template -> new UseSiteRewriter(javac.make(), javac.names(),
                registry, classIndex, resolverOf(template),
                (_, message) -> javac.messager().printMessage(Diagnostic.Kind.ERROR, message, template.element().orElseThrow()));
        this.generator = new SpecializationGenerator(javac, registry, this::resolverOf, rewriters);
        this.bridges = new BridgeInjector(javac, registry, rewriters);
    }

    public void process(RoundEnvironment round) {
        List<TypeElement> types = ElementFilter.typesIn(round.getRootElements()).stream().flatMap(RoundProcessor::withNested).toList();
        Map<JCCompilationUnit, NameResolver> units = types.stream().map(javac::unitOf)
                .collect(Collectors.toMap(Function.identity(), u -> new CompilationUnitResolver(u, registry::typeExistsOrWillBeGenerated),
                        (a, _) -> a, LinkedHashMap::new));
        annotated(types, Annotations.SPECIALIZE).forEach(t -> registry.registerTemplate(t, javac.treeOf(t), javac.unitOf(t)).ifPresent(sources::publish));
        annotated(types, Annotations.SPECIALIZED).forEach(t -> registry.registerSpecialized(t, javac.treeOf(t)));
        annotated(types, Annotations.SPECIALIZE_WITH).forEach(registry::registerRequest);
        types.forEach(type -> specializeMethods(type, units.get(javac.unitOf(type))));

        units.forEach((unit, resolver) -> {
            inlines.collect(unit, resolver);
            classIndex.index(unit, resolver);
        });
        units.forEach((unit, resolver) -> new UsageScanner(registry, resolver).scan(unit.defs));
        try (ConstantEvaluator evaluator = new ConstantEvaluator(javac.fileManager())) {
            units.forEach((unit, resolver) -> rewrite(unit, resolver, evaluator));
        }
        while (generatePass()) { }
        registry.sourceTemplates().stream().filter(SourceTemplate::compiledHere)
                .forEach(template -> bridges.inject(template, resolverOf(template)));
        dump.ifPresent(d -> units.keySet().forEach(d::write));
    }

    private static Stream<TypeElement> withNested(TypeElement type) {
        return Stream.concat(Stream.of(type), ElementFilter.typesIn(type.getEnclosedElements()).stream().flatMap(RoundProcessor::withNested));
    }

    private static Stream<TypeElement> annotated(List<TypeElement> types, String annotation) {
        return types.stream().filter(t -> AnnotationValues.mirror(t, annotation).isPresent());
    }

    /** {@code @Specialize} on static methods of a class: overloads are added before the bodies are rewritten. */
    private void specializeMethods(TypeElement type, NameResolver resolver) {
        JCClassDecl owner = javac.treeOf(type);
        MethodSpecializer specializer = new MethodSpecializer(javac.make(), javac.names(), registry.templateRef(resolver));
        ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                .filter(method -> AnnotationValues.mirror(method, Annotations.SPECIALIZE).isPresent())
                .forEach(method -> registry.reader().specializedMethod(method)
                        .ifPresent(request -> specializer.specialize(owner, javac.methodOf(method), request)));
    }

    private void rewrite(JCCompilationUnit unit, NameResolver resolver, ConstantEvaluator evaluator) {
        Diagnostics diagnostics = (tree, message) -> javac.trees().printMessage(Diagnostic.Kind.ERROR, message, tree, unit);
        ConstEvalRewriter constants = new ConstEvalRewriter(javac.make(), javac.names(), unit, evaluator, diagnostics);
        unit.defs = constants.translate(unit.defs);
        constants.resolve();
        unit.defs = new LoopUnroller(javac.make(), diagnostics).translate(unit.defs);
        unit.defs = new TailRecursionEliminator(javac.make(), javac.names(), diagnostics).translate(unit.defs);
        unit.defs = new InlineExpander(javac.make(), javac.names(), inlines, resolver, new ClassScope(resolver)).translate(unit.defs);
        unit.defs = new UseSiteRewriter(javac.make(), javac.names(), registry, classIndex, resolver, diagnostics).translate(unit.defs);
    }

    /** One pass over every template; {@code true} when any of them wrote a class, which may enable further ones. */
    private boolean generatePass() {
        return registry.sourceTemplates().stream().map(generator::generate).reduce(false, Boolean::logicalOr);
    }

    private NameResolver resolverOf(SourceTemplate template) {
        return templateResolvers.computeIfAbsent(template, t -> new CompilationUnitResolver(t.unit(), registry::typeExistsOrWillBeGenerated));
    }
}
