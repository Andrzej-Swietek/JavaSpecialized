package dev.specialize.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

/** {@code Dict<K, V>}: a specialized key next to a generic value; {@code MyList<T>}: varargs and functional interfaces. */
class DictAndListTest {

    @Test
    void dictKeepsTheValueParameterGeneric() {
        Dict<int, String> byId = Dict.of(1, "one");
        for (int i = 2; i < 40; i++) {
            byId.put(i, "n" + i);
        }
        byId.put(1, "ONE");
        DictInt<String> same = byId;
        assertEquals(39, same.size());
        assertEquals("ONE", byId.get(1));
        assertEquals("n39", byId.getOrElse(39, () -> "?"));
        assertEquals("?", byId.getOrElse(40, () -> "?"));
        assertTrue(byId.containsKey(7) && !byId.containsKey(41));
        assertEquals(int.class, byId.keyType());
        Dict<int, Integer> lengths = byId.mapValues(String::length);
        assertEquals(3, lengths.get(1));
        assertEquals(DictInt.class, lengths.getClass());
        assertEquals("{1=a}", Dict.of(1, "a").toString());

        Dict<long, String> big = Dict.<long, String>empty();
        big.put(1L << 40, "big");
        assertEquals("big", big.get(1L << 40));
        assertEquals(DictLong.class, big.getClass());

        Dict<String, Integer> byName = Dict.of("a", 1);
        assertEquals(DictString.class, byName.getClass());
        assertEquals(String.class, byName.keyType());
        assertEquals(1, byName.get("a"));

        Dict<User, String> generic = Dict.of(new User("Ala", 30), "x");
        assertEquals(Dict.class, generic.getClass());
        assertEquals("x", generic.get(new User("Ala", 30)));
        assertEquals(Object.class, generic.keyType(), "the generic class keeps the generic helper");
        assertEquals("{User[name=Ala, age=30]=x}", generic.toString());
        for (int i = 0; i < 20; i++) {
            generic.put(new User("u" + i, i), "v" + i);
        }
        generic.put(new User("Ala", 30), "y");
        assertEquals(21, generic.size());
        assertTrue(generic.toString().contains(", User[name=u"), generic.toString());
        assertEquals("y", generic.getOrElse(new User("Ala", 30), () -> "?"));
        assertEquals("?", generic.getOrElse(new User("nobody", 0), () -> "?"));
        assertEquals(null, generic.get(new User("nobody", 0)));
        assertTrue(generic.containsKey(new User("u7", 7)) && !generic.containsKey(new User("u7", 8)));
        assertEquals(1, generic.mapValues(String::length).get(new User("Ala", 30)));
        assertEquals(Dict.class, Dict.<User, String>empty().getClass());

        String javap = Bytecode.of(DictInt.class);
        assertTrue(javap.contains("private int[] keys;"), javap);
        assertTrue(javap.contains("public V get(int);"), javap);
        assertTrue(javap.contains("public static <V> dev.specialize.examples.DictInt<V> of(int, V);"), javap);
    }

    @Test
    void jdkSpellingsStandForTheTemplates() {
        List<int> xs = new ArrayList<>();
        xs.add(4);
        ArrayList<int> ys = new ArrayList<>(xs.get(0), 5);
        assertEquals(MyListInt.class, xs.getClass());
        assertEquals(MyListInt.class, ys.getClass());
        assertEquals(9, ys.fold(0, Integer::sum));
        List<Integer> boxed = new ArrayList<>();               // the boxed spelling is the JDK list
        boxed.add(1);
        assertEquals(ArrayList.class, boxed.getClass());
        java.util.Map<int, String> byId = new java.util.HashMap<>();
        byId.put(1, "one");
        assertEquals(DictInt.class, byId.getClass());
        assertEquals("one", byId.get(1));
        assertEquals(6, sum(ys));
    }

    static int sum(List<int> xs) {
        return xs.fold(0, Integer::sum) - 3;
    }

    @Test
    void listSpecializesVarargsAndFunctionalInterfaces() {
        MyList<int> numbers = new MyList<>(3, 1, 2);
        numbers.add(4);
        for (int i = 0; i < 10; i++) {
            numbers.add(i);
        }
        assertEquals(MyListInt.class, numbers.getClass());
        assertEquals(14, numbers.size());
        assertEquals(3, numbers.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> numbers.get(14));
        assertTrue(numbers.contains(9) && !numbers.contains(99));
        assertEquals(49, numbers.filter(x -> x > 2).fold(0, Integer::sum));
        @dev.specialize.Boxed MyList<String> words = numbers.map(Integer::toString); // map's MyList<R> is the generic class
        assertEquals(MyList.class, words.getClass());
        assertEquals("3", words.get(0));
        MyList<String> copy = new MyList<>(words.get(0), words.get(1));
        assertEquals(MyListString.class, copy.getClass());
        List<Integer> seen = new ArrayList<>();
        IntConsumer collect = seen::add;
        numbers.each(collect);
        assertEquals(14, seen.size());
        int total = 0;
        for (int x : numbers) {
            total += x;
        }
        assertEquals(3 + 1 + 2 + 4 + 45, total);
        assertEquals("[3, 1, 2, 4, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9]", numbers.toString());

        MyList<double> doubles = MyList.empty();
        doubles.add(1.5);
        assertEquals(MyListDouble.class, doubles.getClass());
        assertEquals("[1.5]", doubles.toString());
        MyList<String> names = new MyList<>("a", "b");
        assertTrue(names.contains("b"));
        MyList<long> longs = new MyList<>(1L, 2L);
        assertEquals(MyListLong.class, longs.getClass());
        long longTotal = 0;
        for (long x : longs) {
            longTotal += x;
        }
        assertEquals(3L, longTotal);
        double doubleTotal = 0;
        for (double x : doubles) {
            doubleTotal += x;
        }
        assertEquals(1.5, doubleTotal);
        StringBuilder joined = new StringBuilder();
        for (String s : names) {
            joined.append(s);
        }
        assertEquals("ab", joined.toString());

        MyList<User> users = new MyList<>(new User("Ala", 30));
        assertEquals(MyList.class, users.getClass());
        for (int i = 0; i < 5; i++) {
            users.add(new User("u" + i, i));
        }
        assertTrue(users.contains(new User("Ala", 30)) && !users.contains(new User("x", 1)));
        assertEquals(6, users.size());
        assertEquals("Ala", users.get(0).name());
        assertThrows(IndexOutOfBoundsException.class, () -> users.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> users.get(6));
        assertEquals(2, users.filter(u -> u.age() < 2).size());
        assertEquals(30 + 10, users.map(User::age).fold(0, Integer::sum));
        List<String> collected = new ArrayList<>();
        users.each(u -> collected.add(u.name()));
        assertEquals(6, collected.size());
        int count = 0;
        for (User u : users) {
            count += u == null ? 0 : 1;
        }
        assertEquals(6, count);
        assertEquals("[User[name=Ala, age=30]]", new MyList<>(new User("Ala", 30)).toString());
        assertEquals(MyList.class, MyList.<User>empty().getClass());
        users.print();
        numbers.print();

        for (Iterable<?> list : List.of(numbers, doubles, longs, names, users, new MyList<>(new User("x", 1)))) {
            java.util.Iterator<?> exhausted = list.iterator();
            exhausted.forEachRemaining(x -> { });
            assertThrows(java.util.NoSuchElementException.class, exhausted::next, list.getClass().getSimpleName());
        }

        String javap = Bytecode.of(MyListInt.class);
        assertTrue(javap.contains("public dev.specialize.examples.MyListInt(int...);"), javap);
        assertTrue(javap.contains("public dev.specialize.examples.MyListInt filter(java.util.function.IntPredicate);"), javap);
        assertTrue(javap.contains("public void each(java.util.function.IntConsumer);"), javap);
    }
}
