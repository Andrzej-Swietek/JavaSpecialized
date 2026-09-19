package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistryEdgeCasesTest {

    @Test
    void reportsInvalidAnnotationsAndDuplicateGeneratedNames() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Bad", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = {void.class})
                public class Bad<T> { }
                """, "t.NotTemplate", """
                package t;
                import dev.specialize.Specialized;
                @Specialized(of = String.class, type = int.class)
                public class NotTemplate { }
                """, "t.BadType", """
                package t;
                import dev.specialize.Specialized;
                @Specialized(of = Ok.class, type = void.class)
                public class BadType { }
                """, "t.BadOf", """
                package t;
                import dev.specialize.Specialized;
                @Specialized(of = int.class, type = int.class)
                public class BadOf { }
                """, "t.Ok", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = int.class, namePattern = "Same")
                public class Ok<T> { T v; }
                """, "t.Ok2", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = long.class, namePattern = "Same")
                public class Ok2<T> { T v; }
                """, "t.package-info", """
                package t;
                """));
        assertFalse(c.success);
        String errors = c.errors();
        assertTrue(errors.contains("cannot specialize for type void"), errors);
        assertTrue(errors.contains("'of' must be a @Specialize template and 'type' a primitive or plain class"), errors);
        assertTrue(errors.contains("could not write t.Same"), errors);
    }

    @Test
    void classPathTemplatesAndMisnamedSpecializationsFromLibraries() throws Exception {
        CompileHarness lib = CompileHarness.compileUnprocessed(Map.of("lib.Pair", """
                package lib;
                import dev.specialize.Specialize;
                @Specialize public class Pair<@Specialize.Param(types = long.class) K, V> { }
                """, "lib.Box", """
                package lib;
                import dev.specialize.Specialize;
                @Specialize(types = int.class, boxedArguments = dev.specialize.BoxedArguments.SPECIALIZE) public class Box<T> { public T v; }
                """, "lib.BoxLong", """
                package lib;
                import dev.specialize.Specialized;
                @Specialized(of = Box.class, type = String.class) public class BoxLong { }
                """, "lib.BoxInt", """
                package lib;
                import dev.specialize.Specialized;
                @Specialized(of = Box.class, type = int.class, generated = true) public class BoxInt { public int v = 5; }
                """, "lib.BoxString", """
                package lib;
                import dev.specialize.Specialized;
                @Specialized(of = Box.class, type = String.class) public class BoxString { public String v = "s"; }
                """, "lib.Pkt", """
                package lib;
                import dev.specialize.Specialize;
                @Specialize(types = {}, autoscan = true, boxedArguments = dev.specialize.BoxedArguments.SPECIALIZE) public class Pkt<T> { public T v; }
                """, "lib.PktLong", """
                package lib;
                import dev.specialize.Specialized;
                @Specialized(of = Box.class, type = long.class) public class PktLong { }
                """), List.of());
        assertTrue(lib.success, lib.allDiagnostics());
        CompileHarness c = CompileHarness.compile(Map.of("t.Use", """
                package t;
                import lib.*;
                public class Use {
                    public static Object pair() { Pair<Integer, Integer> p = new Pair<>(); return p; }
                    public static Object longBox() { Box<Long> b = new Box<>(); return b; }
                    public static int intBox() { Box<Integer> b = new BoxInt(); return b.v; }
                    public static String stringBox() { Box<String> b = new BoxString(); return b.v; }
                    public static Object pkt() { Pkt<Long> p = new Pkt<>(); return p; }
                    public static Object plain() { Object o = new Object(); return o; }
                }
                """), List.of(lib.classes), List.of());
        assertTrue(c.success, c.allDiagnostics());
        Class<?> use = c.load("t.Use", lib.classes);
        assertEquals("lib.Pair", use.getMethod("pair").invoke(null).getClass().getName());
        assertEquals("lib.Box", use.getMethod("longBox").invoke(null).getClass().getName(), "misnamed BoxLong is ignored");
        assertEquals(5, use.getMethod("intBox").invoke(null));
        assertEquals("s", use.getMethod("stringBox").invoke(null), "conventional explicit specialization found on the class path");
        assertEquals("lib.Pkt", use.getMethod("pkt").invoke(null).getClass().getName(), "PktLong belongs to another template");
    }
}
