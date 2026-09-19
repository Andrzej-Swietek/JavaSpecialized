package dev.specialize.processor;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/** Compiles sources with the processor attached, in this JVM, and exposes diagnostics and the resulting classes. */
final class CompileHarness {
    final boolean success;
    final List<Diagnostic<? extends JavaFileObject>> diagnostics;
    final Path classes;
    final Path generatedSources;

    private CompileHarness(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, Path classes,
                           Path generatedSources) {
        this.success = success;
        this.diagnostics = diagnostics;
        this.classes = classes;
        this.generatedSources = generatedSources;
    }

    /** {@code sources}: qualified class name → source text. */
    static CompileHarness compile(Map<String, String> sources) throws IOException {
        return compile(sources, List.of(), List.of());
    }

    /** Compiles with the processor; {@code classpath} entries are added to the test JVM's class path. */
    static CompileHarness compile(Map<String, String> sources, List<Path> classpath, List<String> extraOptions)
            throws IOException {
        List<String> options = new ArrayList<>(List.of("-processor", SpecializeProcessor.class.getName()));
        options.addAll(extraOptions);
        return run(sources, classpath, options);
    }

    /** Compiles without the processor (a library built by someone who does not use it). */
    static CompileHarness compileUnprocessed(Map<String, String> sources, List<Path> classpath) throws IOException {
        return run(sources, classpath, List.of("-proc:none"));
    }

    /** Compiles real files (a source tree on disk), with the processor. */
    static CompileHarness compileFiles(List<Path> files) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = javac.getStandardFileManager(null, Locale.ROOT, null)) {
            List<JavaFileObject> units = new ArrayList<>();
            fm.getJavaFileObjectsFromPaths(files).forEach(units::add);
            return run(units, List.of(), List.of("-processor", SpecializeProcessor.class.getName()));
        }
    }

    /** Compiles caller-provided file objects, with the processor. */
    static CompileHarness compileObjects(List<JavaFileObject> units) throws IOException {
        return run(units, List.of(), List.of("-processor", SpecializeProcessor.class.getName()));
    }

    private static CompileHarness run(Map<String, String> sources, List<Path> classpath, List<String> options)
            throws IOException {
        List<JavaFileObject> units = new ArrayList<>();
        sources.forEach((name, text) -> units.add(new SimpleJavaFileObject(
                URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        }));
        return run(units, classpath, options);
    }

    private static CompileHarness run(List<JavaFileObject> units, List<Path> classpath, List<String> options)
            throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        Path root = Files.createTempDirectory("specialize-test");
        Path classes = Files.createDirectories(root.resolve("classes"));
        Path generated = Files.createDirectories(root.resolve("generated"));
        StringBuilder cp = new StringBuilder(System.getProperty("java.class.path"));
        for (Path p : classpath) {
            cp.append(java.io.File.pathSeparator).append(p);
        }
        try (StandardJavaFileManager fm = javac.getStandardFileManager(collector, Locale.ROOT, null)) {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classes.toFile()));
            fm.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(generated.toFile()));
            List<String> all = new ArrayList<>(options);
            all.addAll(List.of("-classpath", cp.toString(), "-Xlint:-options"));
            Boolean ok = javac.getTask(null, fm, collector, all, null, units).call();
            return new CompileHarness(Boolean.TRUE.equals(ok), collector.getDiagnostics(), classes, generated);
        }
    }

    /** The main sources of the examples module, keyed by qualified name (one source of truth for the tests). */
    static Map<String, String> examplesSources() throws IOException {
        Path root = Path.of("..", "specialize-examples", "src", "main", "java");
        Map<String, String> result = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String rel = root.relativize(f).toString();
                result.put(rel.substring(0, rel.length() - 5).replace(java.io.File.separatorChar, '.'), Files.readString(f));
            }
        }
        return result;
    }

    String errors() {
        return diagnostics.stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(d -> d.getMessage(Locale.ROOT))
                .collect(Collectors.joining("\n"));
    }

    String allDiagnostics() {
        return diagnostics.stream()
                .map(d -> d.getKind() + " " + (d.getSource() == null ? "" : d.getSource().getName() + ":" + d.getLineNumber())
                        + ": " + d.getMessage(Locale.ROOT))
                .collect(Collectors.joining("\n"));
    }

    private ClassLoader loader;

    /** One loader per compilation, so classes loaded through different calls share identity. */
    ClassLoader loader(Path... alsoOnClassPath) throws IOException {
        if (loader == null) {
            URL[] urls = new URL[alsoOnClassPath.length + 1];
            urls[0] = classes.toUri().toURL();
            for (int i = 0; i < alsoOnClassPath.length; i++) {
                urls[i + 1] = alsoOnClassPath[i].toUri().toURL();
            }
            loader = new URLClassLoader(urls, getClass().getClassLoader());
        }
        return loader;
    }

    Class<?> load(String name, Path... alsoOnClassPath) throws Exception {
        return Class.forName(name, true, loader(alsoOnClassPath));
    }

    String generatedSource(String qualifiedName) throws IOException {
        return Files.readString(generatedSources.resolve(qualifiedName.replace('.', '/') + ".java"));
    }

    boolean generated(String qualifiedName) {
        return Files.exists(generatedSources.resolve(qualifiedName.replace('.', '/') + ".java"));
    }
}
