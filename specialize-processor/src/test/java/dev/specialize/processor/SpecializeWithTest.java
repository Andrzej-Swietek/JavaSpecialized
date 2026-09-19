package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A library template specialized for a type the library never heard of. */
class SpecializeWithTest {

    @Test
    void applicationTypeGetsItsOwnSpecializationOfALibraryTemplate() throws Exception {
        CompileHarness lib = CompileHarness.compile(CompileHarness.examplesSources());
        assertTrue(lib.success, lib.allDiagnostics());
        assertTrue(Files.exists(lib.classes.resolve("META-INF/specialize/dev.specialize.examples.Opt.java")), "template source travels with the jar");

        CompileHarness app = CompileHarness.compile(Map.of("app.UserDTO", """
                package app;
                import dev.specialize.SpecializeWith;
                import dev.specialize.examples.Opt;
                import dev.specialize.examples.OptCodec;
                @SpecializeWith({Opt.class, OptCodec.class})
                public record UserDTO(String name, int age) { }
                """, "app.Service", """
                package app;
                import dev.specialize.examples.Opt;
                import java.util.List;
                public class Service {
                    public static Opt<UserDTO> find(String name) { return name.isEmpty() ? Opt.empty() : Opt.some(new UserDTO(name, 30)); }
                    public static String run() {
                        Opt<UserDTO> u = Opt.some(new UserDTO("Ala", 30));
                        List<Opt<UserDTO>> all = List.of(u, OptUserDTO.empty(), find("Ola"));
                        return u.getClass().getSimpleName() + ":" + all.size() + ":" + find("").isEmpty() + ":" + u.get().name();
                    }
                }
                """), List.of(lib.classes), List.of());
        assertTrue(app.success, app.allDiagnostics());
        assertTrue(app.generated("app.OptUserDTO"), "generated next to the requesting type");
        assertTrue(app.generated("app.OptCodecUserDTO"));
        String source = app.generatedSource("app.OptUserDTO");
        assertTrue(source.contains("package app;") && source.contains("import dev.specialize.examples.*;"), source);
        assertTrue(source.contains("private final app.UserDTO value;"), source);
        assertEquals("OptUserDTO:3:true:Ala", app.load("app.Service", lib.classes).getMethod("run").invoke(null));

        CompileHarness downstream = CompileHarness.compile(Map.of("web.Controller", """
                package web;
                import app.UserDTO;
                import dev.specialize.examples.Opt;
                public class Controller {
                    public static Object show(Opt<UserDTO> user) { return user; }
                    public static Object run() { Opt<UserDTO> u = Opt.some(new UserDTO("Ola", 1)); return show(u); }
                }
                """), List.of(lib.classes, app.classes), List.of());
        assertTrue(downstream.success, downstream.allDiagnostics());
        assertEquals("app.OptUserDTO", downstream.load("web.Controller", lib.classes, app.classes).getMethod("run").invoke(null).getClass().getName());
    }

    @Test
    void requestsInTheTemplatesOwnModuleGetBridges() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Box", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = int.class)
                public class Box<T> { public T v; public static <T> Box<T> of(T v) { Box<T> b = new Box<>(); b.v = v; return b; } }
                """, "t.Dto", """
                package t;
                import dev.specialize.SpecializeWith;
                @SpecializeWith(Box.class)
                public record Dto(String n) { }
                """, "t.Use", """
                package t;
                public class Use { public static Object run() { return java.util.List.of(Box.of(new Dto("d"))).get(0); } }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("t.BoxDto", c.load("t.Use").getMethod("run").invoke(null).getClass().getName(), "Box.of(Dto) bridge");
    }

    @Test
    void existingSpecializationsWinAndTheDefaultPackageWorks() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("Box", """
                import dev.specialize.Specialize;
                @Specialize(types = int.class)
                public class Box<T> { public T v; public static <T> Box<T> of(T v) { Box<T> b = new Box<>(); b.v = v; return b; } }
                """, "BoxDto", """
                import dev.specialize.Specialized;
                @Specialized(of = Box.class, type = Dto.class)
                public class BoxDto { public static BoxDto of(Dto d) { return new BoxDto(); } public String kind() { return "explicit"; } }
                """, "Dto", """
                import dev.specialize.SpecializeWith;
                @SpecializeWith(Box.class)
                public record Dto() { }
                """, "Other", """
                import dev.specialize.SpecializeWith;
                @SpecializeWith(Box.class)
                public record Other() { }
                """, "Plain", """
                public class Plain { }
                """, "Use", """
                public class Use {
                    public static String run() { Box<Dto> d = Box.of(new Dto()); Box<Other> o = Box.of(new Other()); Box<Plain> p = Box.of(new Plain()); return d.kind() + o.getClass().getSimpleName() + p.getClass().getSimpleName(); }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("explicitBoxOtherBox", c.load("Use").getMethod("run").invoke(null));
        assertFalse(c.generated("BoxDto"), "the explicit class wins over the request");
    }

    @Test
    void librariesCompiledWithoutTheProcessorCannotBeSpecializedHere() throws Exception {
        CompileHarness lib = CompileHarness.compileUnprocessed(Map.of("lib.Box", """
                package lib;
                import dev.specialize.Specialize;
                @Specialize(types = int.class) public class Box<T> { public T v; }
                """), List.of());
        assertTrue(lib.success, lib.allDiagnostics());
        CompileHarness app = CompileHarness.compile(Map.of("app.Dto", """
                package app;
                import dev.specialize.SpecializeWith;
                @SpecializeWith({lib.Box.class, String.class})
                public record Dto() { }
                """), List.of(lib.classes), List.of());
        assertFalse(app.success);
        assertTrue(app.errors().contains("the source of lib.Box is not available"), app.errors());
        assertTrue(app.errors().contains("java.lang.String is not a @Specialize template"), app.errors());
    }
}
