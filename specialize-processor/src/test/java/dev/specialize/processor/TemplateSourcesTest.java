package dev.specialize.processor;

import dev.specialize.processor.model.BinaryTemplate;
import dev.specialize.processor.model.SourceTemplate;
import dev.specialize.processor.registry.TemplateSources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class TemplateSourcesTest {
    private final ParserFactory parsers;

    TemplateSourcesTest() {
        ModuleAccess.ensureOpen();
        JavacTaskImpl task = (JavacTaskImpl) ToolProvider.getSystemJavaCompiler().getTask(null, null, null,
                List.of("-proc:none"), List.of("java.lang.Object"), null);
        Context context = task.getContext();
        parsers = ParserFactory.instance(context);
    }

    private static JavaFileObject file(String text) {
        return new SimpleJavaFileObject(URI.create("string:///t/Box.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        };
    }

    @Test
    void publishFailureIsAWarningNotACrash() {
        List<String> messages = new ArrayList<>();
        Messager messager = (Messager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Messager.class},
                (p, m, a) -> messages.add(a[0] + ": " + a[1]));
        Filer failing = (Filer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Filer.class},
                (p, m, a) -> { throw new IOException("disk full"); });
        String text = "package t; public class Box<T> { }";
        JCCompilationUnit unit = parsers.newParser(text, false, false, false).parseCompilationUnit();
        unit.sourcefile = file(text);
        BinaryTemplate declaration = new BinaryTemplate("t.Box", "{Name}{Type}", List.of(dev.specialize.processor.model.Template.TypeParameter.specialized("T", List.of())), List.of(), false, dev.specialize.BoxedArguments.KEEP, java.util.Optional.empty());
        new TemplateSources(failing, parsers, messager).publish(SourceTemplate.compiledHere(declaration, null, unit));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).startsWith("WARNING: @Specialize: cannot store the template source of t.Box"), messages.get(0));
        javax.lang.model.element.TypeElement element = (javax.lang.model.element.TypeElement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{javax.lang.model.element.TypeElement.class}, (p, m, a) -> null);
        BinaryTemplate located = new BinaryTemplate("t.Box", "{Name}{Type}", declaration.parameters(), List.of(), false, dev.specialize.BoxedArguments.KEEP, java.util.Optional.of(element));
        new TemplateSources(failing, parsers, messager).publish(SourceTemplate.compiledHere(located, null, unit));
        assertEquals(2, messages.size(), "reported at the template when it is known: " + messages);
    }

    @Test
    void loadFindsTheTemplateAmongOtherTopLevelClasses() {
        String text = "package t; import java.util.List; class Helper { } public class Box<T> { T v; }";
        Filer filer = (Filer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Filer.class},
                (p, m, a) -> file(text));
        BinaryTemplate declaration = new BinaryTemplate("t.Box", "{Name}{Type}", List.of(dev.specialize.processor.model.Template.TypeParameter.specialized("T", List.of())), List.of(), false, dev.specialize.BoxedArguments.KEEP, java.util.Optional.empty());
        SourceTemplate loaded = new TemplateSources(filer, parsers, null).load(declaration).orElseThrow();
        assertEquals("Box", loaded.tree().name.toString());
        assertTrue(!loaded.compiledHere());
    }
}
