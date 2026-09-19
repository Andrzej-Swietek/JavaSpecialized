package dev.specialize.processor;

import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

/**
 * {@code -Aspecialize.dump=<dir>}: writes every compilation unit as the processor left it (bridges, inlined calls,
 * loops instead of tail calls, unrolled loops, evaluated constants, specialized use sites) — what delombok is to Lombok.
 */
public record SourceDump(Path directory, Messager messager) {
    public static final String OPTION = "specialize.dump";

    public static Optional<SourceDump> fromOptions(Map<String, String> options, Messager messager) {
        return Optional.ofNullable(options.get(OPTION)).map(Path::of).map(dir -> new SourceDump(dir, messager));
    }

    public void write(JCCompilationUnit unit) {
        String pkg = Optional.ofNullable(unit.getPackageName()).map(p -> p.toString().replace('.', '/')).orElse("");
        String name = Path.of(unit.sourcefile.getName()).getFileName().toString();
        Path target = directory.resolve(pkg).resolve(name);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, unit.toString());
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.WARNING, "specialize.dump: cannot write " + target + ": " + e);
        }
    }
}
