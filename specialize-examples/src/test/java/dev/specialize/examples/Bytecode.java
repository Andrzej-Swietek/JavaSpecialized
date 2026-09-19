package dev.specialize.examples;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.spi.ToolProvider;

/** Runs javap on a compiled test/main class so tests can assert what the processor did at compile time. */
final class Bytecode {
    private Bytecode() {
    }

    static String of(Class<?> cls) {
        String resource = cls.getName().replace('.', '/') + ".class";
        Path file = Paths.get(java.net.URI.create(cls.getClassLoader().getResource(resource).toString()));
        ToolProvider javap = ToolProvider.findFirst("javap").orElseThrow();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int rc = javap.run(new PrintWriter(out), new PrintWriter(err), "-c", "-p", file.toString());
        if (rc != 0) {
            throw new IllegalStateException("javap failed: " + err);
        }
        return out.toString();
    }

    /** The {@code Code:} section of one method, located by a substring of its javap header line. */
    static String method(String javap, String headerFragment) {
        int start = javap.indexOf("\n  " + headerFragment); // header lines are indented by two spaces
        if (start < 0) {
            throw new IllegalArgumentException("no method matching '" + headerFragment + "' in\n" + javap);
        }
        int end = javap.indexOf("\n\n", start);
        return javap.substring(start, end < 0 ? javap.length() : end);
    }
}
