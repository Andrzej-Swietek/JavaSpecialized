package dev.specialize.processor.consteval;

import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import dev.specialize.processor.consteval.EvaluationResult.Failure;
import dev.specialize.processor.consteval.EvaluationResult.Value;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Compiles a source file on the side ({@code -proc:none}, the build's own class path), loads the result and reads
 * static fields from it. One side compilation per file, kept for the whole round; {@link #close()} removes the
 * class files.
 */
public final class ConstantEvaluator implements AutoCloseable {
    private sealed interface Compiled permits Loaded, Failed {
    }

    private record Loaded(URLClassLoader loader, Path directory) implements Compiled {
    }

    private record Failed(String diagnostics) implements Compiled {
    }

    private final JavaFileManager build;
    private final Path tempRoot;
    private final Map<URI, Compiled> compiled = new HashMap<>();

    /** {@code build}: the file manager of the compilation being processed, used for every lookup. */
    public ConstantEvaluator(JavaFileManager build) {
        this(build, Path.of(System.getProperty("java.io.tmpdir")));
    }

    public ConstantEvaluator(JavaFileManager build, Path tempRoot) {
        this.build = build;
        this.tempRoot = tempRoot;
    }

    /** The value of {@code binaryName.field} after the class's static initialization; {@code source} is compiled once per unit. */
    public EvaluationResult evaluate(JCCompilationUnit unit, String source, String binaryName, String field) {
        return switch (compiled.computeIfAbsent(unit.sourcefile.toUri(), _ -> compile(unit, source))) {
            case Failed failed -> new Failure("cannot compile the class for evaluation:\n" + failed.diagnostics());
            case Loaded loaded -> read(loaded.loader(), binaryName, field);
        };
    }

    private static EvaluationResult read(ClassLoader loader, String binaryName, String field) {
        try {
            Field f = Class.forName(binaryName, true, loader).getDeclaredField(field);
            f.setAccessible(true);
            return new Value(f.get(null));
        } catch (ReflectiveOperationException | LinkageError e) { // ExceptionInInitializerError: the initializer threw
            return new Failure("evaluation failed: " + Optional.ofNullable(e.getCause()).orElse(e));
        }
    }

    private Compiled compile(JCCompilationUnit unit, String source) {
        try {
            Path out = Files.createTempDirectory(tempRoot, "consteval");
            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
            StandardJavaFileManager local = javac.getStandardFileManager(collector, Locale.ROOT, StandardCharsets.UTF_8);
            local.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(out));
            Optional<Path> root = sourceRoot(unit.sourcefile.toUri(), Optional.ofNullable(unit.getPackageName()).map(Object::toString));
            if (root.isPresent()) {
                local.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of(root.get()));
            }
            try (SideFileManager fm = new SideFileManager(build, local)) {
                List<String> options = List.of("-proc:none", "-implicit:class", "-nowarn", "-Xlint:none");
                if (!javac.getTask(null, fm, collector, options, null, List.of(inMemory(unit.sourcefile.toUri(), source))).call()) {
                    return new Failed(collector.getDiagnostics().stream()
                            .map(d -> "line " + d.getLineNumber() + ": " + d.getMessage(Locale.ROOT))
                            .collect(Collectors.joining("\n")));
                }
            }
            ClassLoader parent = Optional.ofNullable(build.getClassLoader(StandardLocation.CLASS_PATH)).orElseGet(ClassLoader::getPlatformClassLoader);
            return new Loaded(new URLClassLoader(new URL[]{out.toUri().toURL()}, parent), out);
        } catch (IOException e) {
            return new Failed(e.toString());
        }
    }

    /** The source under its original name, so public classes still match their file; the build's own file objects stay untouched. */
    private static JavaFileObject inMemory(URI uri, String text) {
        return new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        };
    }

    /** {@code /src/main/java} for {@code /src/main/java/com/x/Tables.java} in package {@code com.x}; empty for other layouts or in-memory sources. */
    public static Optional<Path> sourceRoot(URI source, Optional<String> packageName) {
        if (!"file".equals(source.getScheme())) {
            return Optional.empty();
        }
        Optional<Path> root = Optional.ofNullable(Path.of(source).getParent());
        List<String> segments = packageName.map(p -> Arrays.asList(p.split("\\."))).orElse(List.of());
        for (String segment : segments.reversed()) {
            root = root.filter(directory -> directory.endsWith(segment)).map(Path::getParent);
        }
        return root;
    }

    @Override
    public void close() {
        compiled.values().forEach(entry -> {
            if (entry instanceof Loaded(URLClassLoader loader, Path directory)) {
                closeQuietly(loader);
                delete(directory.toFile());
            }
        });
    }

    public static void closeQuietly(Closeable resource) {
        try {
            resource.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(File file) {
        Optional.ofNullable(file.listFiles()).stream().flatMap(Arrays::stream).forEach(ConstantEvaluator::delete);
        file.delete();
    }
}
