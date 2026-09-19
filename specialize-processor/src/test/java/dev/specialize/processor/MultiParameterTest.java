package dev.specialize.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Templates with several type parameters: {@code Map2<K, V>} → {@code Map2Int<V>}, {@code Pair<K, V>} → {@code PairIntLong}. */
class MultiParameterTest {

    private static final String MAP2 = """
            package t;
            import dev.specialize.Prim;
            import dev.specialize.Specialize;
            import java.util.function.Function;
            import java.util.function.Supplier;
            @Specialize(boxedArguments = dev.specialize.BoxedArguments.SPECIALIZE)
            public class Map2<@Specialize.Param(types = {int.class, long.class}) K, V> {
                private K[] keys = Prim.newArray(4);
                private final Object[] values = new Object[4];
                private int size;
                public static <K, V> Map2<K, V> of(K key, V value) { Map2<K, V> m = new Map2<>(); m.put(key, value); return m; }
                public static <K, V> Map2<K, V> empty() { return new Map2<>(); }
                public void put(K key, V value) {
                    int i = indexOf(key);
                    if (i < 0) { keys[size] = key; values[size++] = value; } else { values[i] = value; }
                }
                @SuppressWarnings("unchecked")
                public V get(K key) { int i = indexOf(key); return i < 0 ? null : (V) values[i]; }
                public V getOrDefault(K key, Supplier<V> fallback) { V v = get(key); return v == null ? fallback.get() : v; }
                public <R> Map2<K, R> mapValues(Function<V, R> f) {
                    Map2<K, R> out = Map2.<K, R>empty();
                    for (int i = 0; i < size; i++) { out.put(keys[i], f.apply(get(keys[i]))); }
                    return out;
                }
                public K firstKeyOr(K other) { return size == 0 ? Prim.<K>zero() : keys[0]; }
                public Class<?> keyType() { return Prim.<K>type(); }
                public int keyHash() { int h = 0; for (int i = 0; i < size; i++) { h = 31 * h + Prim.hash(keys[i]); } return h; }
                public int size() { return size; }
                public int count(java.util.function.Predicate<? super K> p) { int n = 0; for (int i = 0; i < size; i++) { if (p.test(keys[i])) { n++; } } return n; }
                public Object peek(Supplier<?> s) { return s.get(); }
                public Map2<java.lang.String, V> keyedByName() { return Map2.empty(); }
                static class Node<K> { Map2<K, Object> owner; }
                private int indexOf(K key) { for (int i = 0; i < size; i++) { if (Prim.eq(keys[i], key)) { return i; } } return -1; }
            }
            """;

    private static final String PAIR = """
            package t;
            import dev.specialize.Prim;
            import dev.specialize.Specialize;
            import java.util.function.Function;
            import java.util.function.Supplier;
            @Specialize
            public record Pair<@Specialize.Param(types = {int.class, double.class}) K, @Specialize.Param(types = long.class) V>(K key, V value) {
                public static <K, V> Pair<K, V> of(K key, V value) { return new Pair<>(key, value); }
                public Pair<K, V> withValue(Supplier<V> v) { return new Pair<>(key, v.get()); }
                public String describe(Function<K, V> f) { return Prim.str(key) + "->" + Prim.str(f.apply(key)) + ":" + Prim.eq(value, value); }
                public Pair<K, V> self() { return Pair.of(key, value); }
            }
            """;

    @Test
    void generatesOneClassPerCombinationAndKeepsTheOtherParametersGeneric() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Map2", MAP2, "t.Pair", PAIR, "t.Entry", """
                package t;
                import dev.specialize.Specialize;
                @Specialize public record Entry<@Specialize.Param(types = int.class) K, V>(K key, V value) { }
                """, "t.Use", """
                package t;
                import java.util.function.Supplier;
                public class Use {
                    static Map2<Integer, String> names = Map2.of(1, "one");
                    static Pair<int, long> pl = Pair.of(1, 2L);
                    public static String run() {
                        names.put(2, "two");
                        Map2<int, String> byInt = names;
                        Map2<Long, String> byLong = Map2.<Long, String>empty();
                        byLong.put(7L, "seven");
                        Map2<Integer, Integer> lengths = names.mapValues(String::length);
                        Supplier<Map2<Integer, String>> fresh = () -> Map2.empty();
                        Map2<Integer, String>[] arr = new Map2[1];
                        arr[0] = Map2.of(3, "three");
                        Pair<Integer, Long> p = Pair.of(5, 6L);
                        Pair<Double, Long> d = new Pair<>(1.5, 2L);
                        Map2<Integer, String> diamond = new Map2<>();
                        Entry<Integer, String> e = new Entry<>(4, "four");
                        return diamond.getClass().getSimpleName() + ":" + e.getClass().getSimpleName() + ":" + names.count(k -> k > 1) + ":"
                                + names.keyedByName().getClass().getSimpleName() + ":" + byInt.getClass().getSimpleName() + ":" + byLong.getClass().getSimpleName() + ":" + lengths.get(2)
                                + ":" + fresh.get().size() + ":" + arr[0].getClass().getSimpleName() + ":" + names.firstKeyOr(9)
                                + ":" + byLong.getOrDefault(8L, () -> "none") + ":" + p.getClass().getSimpleName() + ":" + p.self().key()
                                + ":" + d.describe(k -> (long) (k * 2)) + ":" + pl.value() + ":" + d.withValue(() -> 9L).value()
                                + ":" + Map2.<Integer, String>empty().firstKeyOr(4) + ":" + names.keyHash();
                    }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        for (String name : List.of("t.Map2Int", "t.Map2Long", "t.PairIntLong", "t.PairDoubleLong")) {
            assertTrue(c.generated(name), name);
        }
        String map2Int = c.generatedSource("t.Map2Int");
        assertTrue(map2Int.contains("class Map2Int<V>"), map2Int);
        assertTrue(map2Int.contains("private int[] keys = new int[]{") || map2Int.contains("private int[] keys = new int[4]"), map2Int);
        assertTrue(map2Int.contains("public static <V>Map2Int<V> of(int key, V value)"), map2Int);
        assertTrue(map2Int.contains("return int.class;"), map2Int);
        assertTrue(map2Int.contains("Map2Int<R> out = Map2Int.<R>empty();"), map2Int);
        assertTrue(map2Int.contains("public V getOrDefault(int key, Supplier<V> fallback)"), map2Int);
        assertTrue(map2Int.contains("return size == 0 ? 0 : keys[0];"), map2Int);
        assertTrue(map2Int.contains("if ((keys[i] == key))"), map2Int);
        assertTrue(map2Int.contains("@dev.specialize.Specialized(of = t.Map2.class, type = int.class, generated = true)"), map2Int);
        String pair = c.generatedSource("t.PairIntLong");
        assertTrue(pair.contains("public record PairIntLong(int key, long value)"), pair);
        assertTrue(pair.contains("@dev.specialize.Specialized(of = t.Pair.class, types = {int.class, long.class}, generated = true)"), pair);
        assertTrue(pair.contains("public PairIntLong withValue(java.util.function.LongSupplier v)"), pair);
        assertTrue(pair.contains("v.getAsLong()"), pair);
        assertTrue(pair.contains("describe(Function<java.lang.Integer, java.lang.Long> f)"), "two different primitives: no JDK interface\n" + pair);
        assertTrue(pair.contains("public PairIntLong self()") && pair.contains("return PairIntLong.of(key, value);"), pair);
        assertTrue(pair.contains("String.valueOf(key)") && pair.contains("(value == value)"), pair);
        String pairDouble = c.generatedSource("t.PairDoubleLong");
        assertTrue(pairDouble.contains("public record PairDoubleLong(double key, long value)") && pairDouble.contains("Prim.str(f.apply(key))"),
                "a value of unknown type keeps the generic helper\n" + pairDouble);
        String entry = c.generatedSource("t.EntryInt");
        assertTrue(entry.contains("public record EntryInt<V>(int key, V value)"), entry);
        assertTrue(map2Int.contains("public int count(java.util.function.IntPredicate p)"), map2Int);
        assertTrue(map2Int.contains("public Map2<java.lang.String, V> keyedByName()") && map2Int.contains("Map2Int<K, Object> owner;") == false, map2Int);
        assertEquals(2, map2Int.split("@dev.specialize.GeneratedSpecialization").length - 1, "nested classes are marked generated too\n" + map2Int);
        assertEquals("Map2Int:EntryInt:1:Map2:Map2Int:Map2Long:3:0:Map2Int:1:none:PairIntLong:5:1.5->3:true:2:9:0:" + (31 + 2),
                c.load("t.Use").getMethod("run").invoke(null));
        String bridges = c.load("t.Map2").getDeclaredMethods().length + "";
        assertTrue(c.load("t.Map2").getMethod("of", int.class, Object.class).getReturnType().getSimpleName().equals("Map2Int"), bridges);
        assertTrue(c.load("t.Pair").getMethod("of", int.class, long.class).getReturnType().getSimpleName().equals("PairIntLong"));
    }

    @Test
    void autoscanCollectsTuplesAndExplicitSpecializationsTakeSeveralTypes() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.Cell", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(autoscan = true)
                public class Cell<@Specialize.Param(types = {}) R, @Specialize.Param(types = {}) C, V> {
                    public R row; public C col; public V value;
                    public static <R, C, V> Cell<R, C, V> at(R row, C col, V value) { Cell<R, C, V> c = new Cell<>(); c.row = row; c.col = col; c.value = value; return c; }
                }
                """, "t.CellIntInt", """
                package t;
                import dev.specialize.Specialized;
                @Specialized(of = Cell.class, types = {int.class, int.class})
                public class CellIntInt<V> {
                    public int row; public int col; public V value;
                    public static <V> CellIntInt<V> at(int row, int col, V value) { CellIntInt<V> c = new CellIntInt<>(); c.row = row + 100; c.col = col; c.value = value; return c; }
                }
                """, "t.Use", """
                package t;
                public class Use {
                    public static String run() {
                        Cell<int, int, String> a = Cell.at(1, 2, "a");
                        Cell<long, int, String> b = Cell.at(1L, 2, "b");
                        Cell<String, String, String> s = Cell.at("r", "c", "s");
                        return a.getClass().getSimpleName() + ":" + a.row + ":" + b.getClass().getSimpleName() + ":" + b.row + ":" + s.getClass().getSimpleName();
                    }
                }
                """));
        assertTrue(c.success, c.allDiagnostics());
        assertTrue(c.generated("t.CellLongInt"));
        assertFalse(c.generated("t.CellIntInt"), "explicit specialization wins");
        assertEquals("CellIntInt:101:CellLongInt:1:Cell", c.load("t.Use").getMethod("run").invoke(null));
    }

    @Test
    void librariesExposeMultiParameterTemplatesAndSpecializeWithNeedsOneSpecializedParameter() throws Exception {
        CompileHarness lib = CompileHarness.compile(Map.of("t.Map2", MAP2, "t.Pair", PAIR));
        assertTrue(lib.success, lib.allDiagnostics());
        CompileHarness app = CompileHarness.compile(Map.of("app.Use", """
                package app;
                import dev.specialize.SpecializeWith;
                import t.Map2;
                import t.Pair;
                @SpecializeWith(Map2.class)
                public record Use(String id) {
                    public static String run() {
                        Map2<Long, String> m = Map2.of(1L, "x");
                        Map2<Use, String> byUse = Map2.of(new Use("a"), "y");
                        Pair<Integer, Long> p = Pair.of(1, 2L);
                        return m.getClass().getSimpleName() + ":" + byUse.getClass().getSimpleName() + ":" + p.getClass().getSimpleName();
                    }
                }
                """, "app.Bad", """
                package app;
                import dev.specialize.SpecializeWith;
                @SpecializeWith(t.Pair.class)
                public record Bad(int x) { }
                """), List.of(lib.classes), List.of());
        assertFalse(app.success);
        assertTrue(app.errors().contains("@SpecializeWith: t.Pair must specialize exactly one type parameter"), app.errors());
        CompileHarness ok = CompileHarness.compile(Map.of("app.Use", """
                package app;
                import dev.specialize.SpecializeWith;
                import t.Map2;
                import t.Pair;
                @SpecializeWith(Map2.class)
                public record Use(String id) {
                    public static String run() {
                        Map2<Long, String> m = Map2.of(1L, "x");
                        Map2<Use, String> byUse = Map2.of(new Use("a"), "y");
                        Pair<Integer, Long> p = Pair.of(1, 2L);
                        return m.getClass().getSimpleName() + ":" + byUse.getClass().getSimpleName() + ":" + p.getClass().getSimpleName();
                    }
                }
                """), List.of(lib.classes), List.of());
        assertTrue(ok.success, ok.allDiagnostics());
        String map2Use = ok.generatedSource("app.Map2Use");
        assertTrue(map2Use.contains("class Map2Use<V>"), map2Use);
        assertTrue(map2Use.contains("private app.Use[] keys = new app.Use[4];"), map2Use);
        assertTrue(map2Use.contains("return app.Use.class;"), map2Use);
        assertEquals("Map2Long:Map2Use:PairIntLong", ok.load("app.Use", lib.classes).getMethod("run").invoke(null));
    }

    @Test
    void declarationErrors() throws Exception {
        CompileHarness c = CompileHarness.compile(Map.of("t.NoParam", """
                package t;
                import dev.specialize.Specialize;
                @Specialize public class NoParam { }
                """, "t.ClassTypes", """
                package t;
                import dev.specialize.Specialize;
                @Specialize(types = int.class) public class ClassTypes<@Specialize.Param K, V> { }
                """, "t.BadParamType", """
                package t;
                import dev.specialize.Specialize;
                @Specialize public class BadParamType<@Specialize.Param(types = void.class) K, V> { }
                """, "t.Pair", PAIR, "t.WrongCount", """
                package t;
                import dev.specialize.Specialized;
                @Specialized(of = Pair.class, type = int.class) public class WrongCount { }
                """, "t.PairIntLong", """
                package t;
                public class PairIntLong { }
                """));
        assertFalse(c.success);
        String errors = c.errors();
        assertTrue(errors.contains("@Specialize: the template must declare a type parameter"), errors);
        assertTrue(errors.contains("give the types on @Specialize.Param, not on @Specialize"), errors);
        assertTrue(errors.contains("cannot specialize for type void"), errors);
        assertTrue(errors.contains("@Specialized: Pair specializes 2 type parameter(s); give exactly that many in 'types'"), errors);
        assertTrue(errors.contains("@Specialized(of = Pair.class, types = {int.class, long.class})"), errors);
    }
}
