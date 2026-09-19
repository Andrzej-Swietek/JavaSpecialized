package dev.specialize.processor;

import dev.specialize.processor.model.PrimitiveTarget;
import dev.specialize.processor.model.ReferenceTarget;
import dev.specialize.processor.model.TargetType;
import dev.specialize.processor.registry.AnnotationValues;
import dev.specialize.processor.specialize.PrimitiveArgumentRewriter;
import dev.specialize.processor.resolve.Annotations;
import dev.specialize.processor.registry.SpecRegistry;
import dev.specialize.processor.registry.TemplateSources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.processing.JavacMessager;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class InfrastructureTest {


    @Test
    void moduleAccessOnlyExportsWhatIsMissingAndReportsFailures() {
        ModuleAccess.ensureOpen(); // real path (already open after the first compile in this JVM, or opened now)
        Module self = InfrastructureTest.class.getModule();
        List<String> exported = new ArrayList<>();
        ModuleAccess.ensureOpen(ModuleLayer.boot(), self, List.of("com.sun.tools.javac.tree", "com.sun.tools.javac.file"),
                (from, pkg, to) -> exported.add(pkg));
        assertEquals(List.of("com.sun.tools.javac.file"), exported);
        ModuleAccess.ensureOpen(ModuleLayer.boot(), self, List.of("com.sun.tools.javac.tree"),
                (from, pkg, to) -> { throw new AssertionError("must not be called: already exported"); });

        IllegalStateException failed = assertThrows(IllegalStateException.class, () -> ModuleAccess.ensureOpen(
                ModuleLayer.boot(), self, List.of("com.sun.tools.javac.launcher"),
                (from, pkg, to) -> { throw new UnsupportedOperationException("no unsafe"); }));
        assertTrue(failed.getMessage().contains("-J--add-exports=jdk.compiler/com.sun.tools.javac.launcher=ALL-UNNAMED"), failed.getMessage());
        assertTrue(failed.getMessage().contains("no unsafe"), failed.getMessage());

        IllegalStateException noModule = assertThrows(IllegalStateException.class, () -> ModuleAccess.ensureOpen(
                ModuleLayer.empty(), self, ModuleAccess.PACKAGES, ModuleAccess::exportWithUnsafe));
        assertTrue(noModule.getMessage().contains("jdk.compiler module not found"));
    }


    @Test
    void processorReportsAnUnsupportedEnvironmentInsteadOfCrashing() {
        List<String> reported = new ArrayList<>();
        ProcessingEnvironment fake = fakeEnvironment(reported);
        SpecializeProcessor processor = new SpecializeProcessor();
        processor.init(fake);
        assertEquals(SourceVersion.latest(), processor.getSupportedSourceVersion());
        RoundEnvironment round = (RoundEnvironment) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RoundEnvironment.class}, (p, m, a) -> m.getName().equals("processingOver") ? false : Set.of());
        assertFalse(processor.process(Set.of(), round));
        assertFalse(processor.process(Set.of(), round));
        assertEquals(1, reported.size(), reported.toString());
        assertTrue(reported.get(0).contains("unsupported ProcessingEnvironment"), reported.get(0));
    }

    @Test
    void processorReportsAnyStartupFailureWithItsStackTrace() {
        List<String> reported = new ArrayList<>();
        JavacProcessingEnvironment real = realEnvironment();
        ProcessingEnvironment throwing = new Wrapper(real) {
            @Override public Map<String, String> getOptions() { throw new UnsupportedOperationException("no options"); }
            @Override public Messager getMessager() { return fakeEnvironment(reported).getMessager(); }
        };
        SpecializeProcessor processor = new SpecializeProcessor();
        processor.init(throwing);
        RoundEnvironment round = (RoundEnvironment) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RoundEnvironment.class}, (p, m, a) -> m.getName().equals("processingOver") ? false : Set.of());
        assertFalse(processor.process(Set.of(), round));
        assertEquals(1, reported.size(), reported.toString());
        assertTrue(reported.get(0).contains("failed to start (java.lang.UnsupportedOperationException: no options)"), reported.get(0));
        assertTrue(reported.get(0).contains("SpecializeProcessor.init"), reported.get(0));
    }

    @Test
    void voidIsNeverMarkedAsAPrimitiveTypeArgument() {
        assertTrue(PrimitiveTarget.of(com.sun.tools.javac.code.TypeTag.VOID).isEmpty());
        Context context = ((JavacTaskImpl) ToolProvider.getSystemJavaCompiler().getTask(null, null, null,
                List.of("-proc:none"), List.of("java.lang.Object"), null)).getContext();
        com.sun.tools.javac.tree.TreeMaker make = com.sun.tools.javac.tree.TreeMaker.instance(context);
        com.sun.tools.javac.util.Names names = com.sun.tools.javac.util.Names.instance(context);
        com.sun.tools.javac.tree.JCTree.JCExpression argument = make.TypeIdent(com.sun.tools.javac.code.TypeTag.VOID);
        com.sun.tools.javac.tree.JCTree.JCTypeApply type = make.TypeApply(make.Ident(names.fromString("Box")),
                com.sun.tools.javac.util.List.of(argument));
        new PrimitiveArgumentRewriter(make, names).translate(type);
        assertSame(argument, type.arguments.head, "a void argument cannot be boxed, javac rejects it later");
    }

    private static ProcessingEnvironment fakeEnvironment(List<String> reported) {
        Messager messager = (Messager) Proxy.newProxyInstance(InfrastructureTest.class.getClassLoader(),
                new Class<?>[]{Messager.class}, (p, m, a) -> {
                    reported.add(String.valueOf(a[1]));
                    return null;
                });
        return new ProcessingEnvironment() {
            @Override public Map<String, String> getOptions() { return Map.of(); }
            @Override public Messager getMessager() { return messager; }
            @Override public Filer getFiler() { return null; }
            @Override public Elements getElementUtils() { return null; }
            @Override public Types getTypeUtils() { return null; }
            @Override public SourceVersion getSourceVersion() { return SourceVersion.latest(); }
            @Override public Locale getLocale() { return Locale.ROOT; }
        };
    }


    static class Wrapper implements ProcessingEnvironment {
        static ProcessingEnvironment STATIC_IGNORED = null;
        private final Object unrelated = new Object();
        private final ProcessingEnvironment nullFirst = null;
        private final ProcessingEnvironment delegate;
        Wrapper(ProcessingEnvironment delegate) { this.delegate = delegate; }
        @Override public Map<String, String> getOptions() { return delegate.getOptions(); }
        @Override public Messager getMessager() { return delegate.getMessager(); }
        @Override public Filer getFiler() { return delegate.getFiler(); }
        @Override public Elements getElementUtils() { return delegate.getElementUtils(); }
        @Override public Types getTypeUtils() { return delegate.getTypeUtils(); }
        @Override public SourceVersion getSourceVersion() { return delegate.getSourceVersion(); }
        @Override public Locale getLocale() { return delegate.getLocale(); }
    }

    static final class Handler implements InvocationHandler {
        final ProcessingEnvironment target;
        Handler(ProcessingEnvironment target) { this.target = target; }
        @Override public Object invoke(Object proxy, java.lang.reflect.Method m, Object[] args) throws Throwable { return m.invoke(target, args); }
    }

    static final class SelfReferencing implements ProcessingEnvironment {
        private final ProcessingEnvironment me = this;
        @Override public Map<String, String> getOptions() { return Map.of(); }
        @Override public Messager getMessager() { return null; }
        @Override public Filer getFiler() { return null; }
        @Override public Elements getElementUtils() { return null; }
        @Override public Types getTypeUtils() { return null; }
        @Override public SourceVersion getSourceVersion() { return SourceVersion.latest(); }
        @Override public Locale getLocale() { return Locale.ROOT; }
    }

    static final class EmptyHandler implements InvocationHandler {
        @Override public Object invoke(Object proxy, java.lang.reflect.Method m, Object[] args) { return null; }
    }

    private static JavacProcessingEnvironment realEnvironment() {
        ModuleAccess.ensureOpen();
        JavacTaskImpl task = (JavacTaskImpl) ToolProvider.getSystemJavaCompiler().getTask(null, null, null,
                List.of("-proc:none"), List.of("java.lang.Object"), null);
        Context context = task.getContext();
        return JavacProcessingEnvironment.instance(context);
    }

    @Test
    void unwrapSeesThroughGradleAndIntelliJStyleWrappers() {
        JavacProcessingEnvironment real = realEnvironment();
        assertSame(real, ProcessingEnvironments.unwrap(real));
        assertSame(real, ProcessingEnvironments.unwrap(new Wrapper(new Wrapper(real))));
        ProcessingEnvironment proxied = (ProcessingEnvironment) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ProcessingEnvironment.class}, new Handler(new Wrapper(real)));
        assertSame(real, ProcessingEnvironments.unwrap(proxied));

        ProcessingEnvironment emptyProxy = (ProcessingEnvironment) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ProcessingEnvironment.class}, new EmptyHandler());
        assertTrue(assertThrows(IllegalStateException.class, () -> ProcessingEnvironments.unwrap(emptyProxy))
                .getMessage().contains("no delegate field found"));

        assertTrue(assertThrows(IllegalStateException.class, () -> ProcessingEnvironments.unwrap(new SelfReferencing()))
                .getMessage().contains("no delegate field found"));

        ProcessingEnvironment tooDeep = real;
        for (int i = 0; i < 6; i++) {
            tooDeep = new Wrapper(tooDeep);
        }
        ProcessingEnvironment finalTooDeep = tooDeep;
        assertTrue(assertThrows(IllegalStateException.class, () -> ProcessingEnvironments.unwrap(finalTooDeep))
                .getMessage().contains("unsupported ProcessingEnvironment"));

        // a holder in a module that is exported but not opened to us: the field is skipped instead of crashing
        JavacMessager messager = (JavacMessager) real.getMessager();
        assertTrue(ProcessingEnvironments.fieldOfType(messager, ProcessingEnvironment.class).isEmpty());
        assertTrue(ProcessingEnvironments.fieldOfType(new Object(), ProcessingEnvironment.class).isEmpty());
    }


    @Test
    void targetTypeIdentity() {
        assertThrows(IllegalArgumentException.class, () -> TargetType.primitive("string"));
        TargetType intType = TargetType.ofQualified("java.lang.Integer");
        assertTrue(intType instanceof PrimitiveTarget);
        assertEquals("Int", intType.suffix());
        assertEquals(TargetType.primitive("int"), intType);
        assertEquals(TargetType.primitive("int").hashCode(), intType.hashCode());
        TargetType user = TargetType.ofQualified("a.b.User");
        assertTrue(user instanceof ReferenceTarget);
        assertEquals("User", user.suffix());
        assertEquals("a.b.User", user.boxed());
        assertTrue(PrimitiveTarget.of("char").jdkFunctionalPrefix().isEmpty());
        assertEquals("Boolean", PrimitiveTarget.of("boolean").jdkFunctionalPrefix().orElseThrow());
        assertNotEquals(user, intType);
    }

    @Test
    void annotationHelpersToleratePlainElements() {
        JavacProcessingEnvironment real = realEnvironment();
        Elements elements = real.getElementUtils();
        SpecRegistry registry = new SpecRegistry(elements, real.getMessager(),
                new TemplateSources(real.getFiler(), com.sun.tools.javac.parser.ParserFactory.instance(real.getContext()), real.getMessager()));
        TypeElement string = elements.getTypeElement("java.lang.String");
        assertTrue(AnnotationValues.mirror(string, Annotations.SPECIALIZE).isEmpty());
        assertTrue(registry.template("java.lang.String").isEmpty());
        assertTrue(registry.specByName("java.lang.String").isEmpty());
        assertTrue(registry.typeExists("java.lang.String"));
        registry.forget("java.lang.String");
        assertTrue(registry.typeExists("java.lang.String"));
        assertTrue(registry.sourceTemplates().isEmpty());
    }
}
