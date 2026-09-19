package dev.specialize.processor;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Entry point registered via {@code META-INF/services}. No signature mentions a {@code com.sun.tools.javac} type:
 * javac loads this class before {@link ModuleAccess} opens the module.
 */
@SupportedAnnotationTypes("*")
public final class SpecializeProcessor extends AbstractProcessor {

    private Optional<RoundProcessor> backend = Optional.empty();
    private Optional<String> initFailure = Optional.empty();

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(SourceDump.OPTION);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        try {
            ModuleAccess.ensureOpen();
            backend = Optional.of(new RoundProcessor(Javac.of(env), SourceDump.fromOptions(env.getOptions(), env.getMessager())));
        } catch (RuntimeException | LinkageError e) {
            initFailure = Optional.of(e instanceof IllegalStateException ? e.getMessage()
                    : "specialize: the processor failed to start (" + e + "); nothing will be specialized. Report this with the stack trace: "
                            + Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        initFailure.ifPresent(message -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message));
        initFailure = Optional.empty();
        backend.filter(_ -> !roundEnv.processingOver()).ifPresent(b -> b.process(roundEnv));
        return false;
    }
}
