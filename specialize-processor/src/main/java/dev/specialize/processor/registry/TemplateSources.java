package dev.specialize.processor.registry;

import dev.specialize.processor.model.BinaryTemplate;
import dev.specialize.processor.model.SourceTemplate;

import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/** A library compiled with the processor carries {@code META-INF/specialize/<template>.java}; later compilations specialize the template from that copy. */
public final class TemplateSources {
    private static final String DIRECTORY = "META-INF/specialize/";
    private static final String PACKAGE = "META-INF.specialize";
    private static final String EXTENSION = JavaFileObject.Kind.SOURCE.extension;

    private final Filer filer;
    private final ParserFactory parsers;
    private final Messager messager;

    public TemplateSources(Filer filer, ParserFactory parsers, Messager messager) {
        this.filer = filer;
        this.parsers = parsers;
        this.messager = messager;
    }

    /** Names of every template a library on the class path published; empty when the file manager cannot list resources. */
    public static List<String> publishedNames(JavaFileManager fileManager) {
        try {
            Iterable<JavaFileObject> published = fileManager.list(StandardLocation.CLASS_PATH, PACKAGE, Set.of(JavaFileObject.Kind.SOURCE), false);
            return StreamSupport.stream(published.spliterator(), false).map(TemplateSources::templateName).toList();
        } catch (IOException | RuntimeException e) { // Gradle's file manager wraps UnsupportedOperationException
            return List.of();
        }
    }

    private static String templateName(JavaFileObject published) {
        String path = published.getName().replace('\\', '/');
        String file = path.substring(path.lastIndexOf('/') + 1);
        return file.substring(0, file.length() - EXTENSION.length());
    }

    public void publish(SourceTemplate template) {
        Element[] origin = template.element().stream().toArray(Element[]::new);
        try {
            CharSequence source = template.unit().getSourceFile().getCharContent(true);
            FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", DIRECTORY + template.qualified() + EXTENSION, origin);
            try (Writer out = resource.openWriter()) {
                out.append(source);
            }
        } catch (IOException e) {
            String message = "@Specialize: cannot store the template source of " + template.qualified()
                    + " (" + e.getMessage() + "); other modules will not be able to specialize it";
            template.element().ifPresentOrElse(
                    element -> messager.printMessage(Diagnostic.Kind.WARNING, message, element),
                    () -> messager.printMessage(Diagnostic.Kind.WARNING, message));
        }
    }

    /** The template class and its compilation unit, parsed from the resource a library compilation left behind. */
    public Optional<SourceTemplate> load(BinaryTemplate declaration) {
        try {
            FileObject resource = filer.getResource(StandardLocation.CLASS_PATH, "", DIRECTORY + declaration.qualified() + EXTENSION);
            JCCompilationUnit unit = parsers.newParser(resource.getCharContent(true), false, false, false).parseCompilationUnit(); // keepDocComments, keepEndPos, keepLineMap
            return unit.defs.stream()
                    .filter(def -> def instanceof JCClassDecl cd && cd.name.contentEquals(declaration.simple()))
                    .map(JCClassDecl.class::cast)
                    .findFirst()
                    .map(tree -> SourceTemplate.fromLibrary(declaration, tree, unit));
        } catch (IOException notPublished) {
            return Optional.empty();
        }
    }
}
