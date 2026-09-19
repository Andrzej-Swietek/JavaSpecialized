package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code -Aspecialize.dump}: the rewritten sources, readable like delombok output. */
class SourceDumpTest {

    @Test
    void writesEveryUnitAsRewritten() throws Exception {
        Path dir = Files.createTempDirectory("specialize-dump");
        CompileHarness c = CompileHarness.compile(Map.of("t.R", """
                package t;
                import dev.specialize.TailRec;
                import dev.specialize.Unroll;
                public final class R {
                    @TailRec public static long gcd(long a, long b) { return b == 0 ? a : gcd(b, a % b); }
                    @Unroll public static int sum(int[] a) { int s = 0; for (int i = 0; i < 3; i++) { s += a[i]; } return s; }
                }
                """, "Top", """
                public class Top { }
                """), List.of(), List.of("-Aspecialize.dump=" + dir));
        assertTrue(c.success, c.allDiagnostics());
        String r = Files.readString(dir.resolve("t/R.java"));
        assertTrue(r.contains("package t;") && r.contains("while (true)") && r.contains("continue;"), r);
        assertTrue(r.contains("s += a[0];") && r.contains("s += a[2];") && !r.contains("i < 3"), r);
        assertTrue(Files.exists(dir.resolve("Top.java")), "default package lands at the root");
    }

    @Test
    void unwritableTargetIsAWarning() throws Exception {
        Path file = Files.createTempFile("not-a-dir", ".txt");
        CompileHarness c = CompileHarness.compile(Map.of("t.X", "package t; public class X { }"),
                List.of(), List.of("-Aspecialize.dump=" + file));
        assertTrue(c.success, c.allDiagnostics());
        assertTrue(c.allDiagnostics().contains("WARNING") && c.allDiagnostics().contains("specialize.dump: cannot write"), c.allDiagnostics());
    }
}
