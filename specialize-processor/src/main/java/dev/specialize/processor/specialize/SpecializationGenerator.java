package dev.specialize.processor.specialize;

import dev.specialize.processor.Javac;
import dev.specialize.processor.model.GeneratedSpecialization;
import dev.specialize.processor.model.SourceTemplate;
import dev.specialize.processor.model.Specialization;
import dev.specialize.processor.model.TargetTuple;
import dev.specialize.processor.registry.SpecRegistry;
import dev.specialize.processor.registry.UsageScanner;
import dev.specialize.processor.resolve.NameResolver;
import dev.specialize.processor.rewrite.UseSiteRewriter;

import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/** Writes the specialized copies of a source template as new source files. */
public final class SpecializationGenerator {
    private final Javac javac;
    private final SpecRegistry registry;
    private final Function<SourceTemplate, NameResolver> resolvers;
    private final Function<SourceTemplate, UseSiteRewriter> rewriters;

    public SpecializationGenerator(Javac javac, SpecRegistry registry, Function<SourceTemplate, NameResolver> resolvers,
                            Function<SourceTemplate, UseSiteRewriter> rewriters) {
        this.javac = javac;
        this.registry = registry;
        this.resolvers = resolvers;
        this.rewriters = rewriters;
    }

    /** Writes every specialization of {@code template} that does not exist yet; {@code true} if anything was written. */
    public boolean generate(SourceTemplate template) {
        List<GeneratedSpecialization> fresh = Stream.concat(template.requests().stream(),
                        template.tuples().stream().map(tuple -> registry.specializationFor(template, tuple).orElseThrow()))
                .<GeneratedSpecialization>mapMulti((spec, sink) -> {
                    if (spec instanceof GeneratedSpecialization generated) {
                        sink.accept(generated);
                    }
                })
                .filter(generated -> template.markGenerated(generated.type()))
                .toList(); // snapshot: autoscan may grow tuples() while writing
        fresh.forEach(generated -> write(template, generated));
        return !fresh.isEmpty();
    }

    private void write(SourceTemplate template, GeneratedSpecialization spec) {
        Element[] origin = template.element().stream().toArray(Element[]::new);
        if (registry.typeExists(spec.qualified())) {
            javac.messager().printMessage(Diagnostic.Kind.ERROR, "@Specialize: " + spec.qualified() + " already exists. "
                    + "Annotate it with @Specialized(of = " + template.simple() + ".class, " + typesClause(spec.type())
                    + ") to make it an explicit specialization, or exclude " + spec.type().key() + " from types.", origin[0]);
            return;
        }
        NameResolver resolver = resolvers.apply(template);
        JCClassDecl copy = new TemplateSpecializer(javac.make(), javac.names(), template, spec.type(), spec.qualified(),
                registry.templateRef(resolver)).specialize(template.tree());
        new UsageScanner(registry, resolver).scan(copy.defs);
        JCClassDecl rewritten = rewriters.apply(template).translate(copy);
        try {
            JavaFileObject file = javac.filer().createSourceFile(spec.qualified(), origin);
            try (Writer out = file.openWriter()) {
                out.write(SourceRenderer.render(template, spec, rewritten));
            }
            registry.forget(spec.qualified()); // the file exists now; drop the cached "does not exist"
        } catch (IOException e) {
            javac.messager().printMessage(Diagnostic.Kind.ERROR, "@Specialize: could not write " + spec.qualified() + " ("
                    + e.getMessage() + "); the specialization is missing from this build", origin[0]);
        }
    }

    private static String typesClause(TargetTuple tuple) {
        return tuple.isSingle()
                ? "type = " + tuple.single().key() + ".class"
                : tuple.targets().stream().map(t -> t.key() + ".class").collect(Collectors.joining(", ", "types = {", "}"));
    }
}
