package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.specialize.processor.registry.TemplateSources;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import javax.tools.JavaFileManager;
import org.junit.jupiter.api.Test;

/** {@code @Specialize(standsFor = …)}: JDK types written with primitives become the template's specializations. */
class AliasTest {
    private static final String INTS = """
            package lib;
            import dev.specialize.Prim;
            import dev.specialize.Specialize;
            import java.util.ArrayList;
            import java.util.List;
            @Specialize(types = int.class, autoscan = true, standsFor = {List.class, ArrayList.class})
            public class Ints<T> {
                private T[] items = Prim.newArray(4);
                private int size;
                public Ints() { }
                public Ints(int capacity) { items = Prim.newArray(capacity); }
                public static <T> Ints<T> of(T a, T b) { Ints<T> l = new Ints<>(); l.add(a); l.add(b); return l; }
                public boolean add(T item) { items[size++] = item; return true; }
                public T get(int i) { return items[i]; }
                public int size() { return size; }
            }
            """;
    private static final String TABLE = """
            package lib;
            import dev.specialize.Prim;
            import dev.specialize.Specialize;
            import java.util.HashMap;
            import java.util.Map;
            @Specialize(standsFor = {Map.class, HashMap.class})
            public class Table<@Specialize.Param(types = int.class) K, V> {
                private K key; private V value;
                public V put(K k, V v) { key = k; value = v; return v; }
                public V get(K k) { return Prim.eq(key, k) ? value : null; }
            }
            """;

    @Test
    void primitiveSpellingOfTheJdkTypeSelectsTheTemplate() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("lib.Ints", INTS, "lib.Table", TABLE, "t.Use", """
                package t;
                import java.util.ArrayList;
                import java.util.HashMap;
                import java.util.List;
                import java.util.Map;
                public class Use {
                    static List<long> longs = new ArrayList<>();
                    static int total(List<int> xs) { int s = 0; for (int i = 0; i < xs.size(); i++) { s += xs.get(i); } return s; }
                    public static String run() {
                        List<int> xs = new ArrayList<>();
                        xs.add(1);
                        ArrayList<int> ys = new ArrayList<>(8);
                        ys.add(2);
                        List<int> zs = List.of(3, 4);
                        List<Integer> boxed = new ArrayList<>();
                        boxed.add(5);
                        Map<int, String> m = new HashMap<>();
                        m.put(1, "one");
                        longs.add(7L);
                        return xs.getClass().getSimpleName() + ":" + ys.getClass().getSimpleName() + ":" + zs.getClass().getSimpleName()
                                + ":" + boxed.getClass().getSimpleName() + ":" + m.getClass().getSimpleName() + ":" + m.get(1)
                                + ":" + total(zs) + ":" + longs.getClass().getSimpleName();
                    }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertEquals("IntsInt:IntsInt:IntsInt:ArrayList:TableInt:one:7:IntsLong", c.load("t.Use").getMethod("run").invoke(null));
        assertTrue(c.generated("lib.IntsLong"), "autoscan sees the alias spelling too");
    }

    @Test
    void libraryTemplatesAreDiscoveredWithoutBeingNamed() throws Exception {
        CompileHarness lib = CompileHarness.compile(Map.of("lib.Ints", INTS));
        assertTrue(lib.success, lib.allDiagnostics());
        CompileHarness app = CompileHarness.compile(Map.of("app.Use", """
                package app;
                import java.util.ArrayList;
                import java.util.List;
                public class Use {
                    public static String run() { List<int> xs = new ArrayList<>(); xs.add(9); return xs.getClass().getName() + xs.get(0); }
                }
                """), List.of(lib.classes), List.of());
        assertTrue(app.success, app.allDiagnostics());
        assertEquals("lib.IntsInt9", app.load("app.Use", lib.classes).getMethod("run").invoke(null));
    }

    @Test
    void invalidAliases() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("lib.Ints", INTS, "lib.Other", """
                package lib;
                import dev.specialize.Specialize;
                @Specialize(standsFor = java.util.List.class) public class Other<T> { }
                """, "lib.Prim", """
                package lib;
                import dev.specialize.Specialize;
                @Specialize(standsFor = int.class) public class Prim<T> { }
                """));
        assertFalse(c.success);
        assertTrue(c.errors().contains("@Specialize: java.util.List is already taken by lib."), c.errors());
        assertTrue(c.errors().contains("@Specialize: standsFor must name classes or interfaces"), c.errors());
    }

    @Test
    void discoveryToleratesFileManagersThatCannotList() {
        JavaFileManager broken = (JavaFileManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{JavaFileManager.class},
                (proxy, method, args) -> { throw new java.io.IOException("no listing"); });
        assertTrue(TemplateSources.publishedNames(broken).isEmpty());
    }
}
