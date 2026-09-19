package dev.specialize.examples;

import dev.specialize.Specialize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Demo {
    private Demo() {
    }


    static Opt<int> firstEven(int[] values) {
        for (int v : values) {
            if (v % 2 == 0) {
                return Opt.some(v);
            }
        }
        return Opt.empty();
    }

    record Payload(
            int id,
            String data,
            Opt<int> value,
            Opt<String> optional
    ) { }

    static Opt<Integer> firstEvenInteger(Integer[] values) {
        for (int v : values) {
            if (v % 2 == 0) {
                return Opt.some(v);
            }
        }
        return Opt.empty();
    }

    static User getUser(String name) {
        return name.isEmpty() ? null : new User(name, 30);
    }

    public static void main(String[] args) {
        Opt<int> a = Opt.some(5);              // → OptInt a = OptInt.some(5)
        Opt<int> b = Opt.<int>some(7);         // → OptInt b = OptInt.some(7)
        Opt<long> big = Opt.some(1L << 40);    // → OptLong
        Opt<String> s = Opt.some("text");      // → OptString (generated from the template for String)
        Opt<User> u = Opt.some(new User("Ala", 30)); // → OptUser (explicit, hand-written)
        Opt<User> nobody = Opt.empty();

        int sum = a.get() + b.get();           // int get(), no unboxing
        long l = big.get();
        List<Opt<int>> list = List.of(Opt.some(1), Opt.<int>empty());  // List<OptInt>
        Opt<User> maybeUser = Opt.of(getUser(""));  // → OptUser.of(null) == OptUser.empty()
        Opt<User> someUser = Opt.of(getUser("Ola"));
        int normal = MathX.normal(0);

        Opt<Integer> integer = Opt.some(5);
        IO.println(integer.map(String::valueOf));
        IO.println("integer.getClass(): " + integer.getClass().getSimpleName());

        IO.println(a + " " + b + " " + big + " " + s + " " + u + " " + nobody);
        IO.println("a.getClass() = " + ((Object) a).getClass().getSimpleName());
        IO.println("OptInt.valueType() = " + OptInt.valueType());
        IO.println("firstEven = " + firstEven(new int[]{1, 3, 8}) + " / " + firstEven(new int[]{1}));
        IO.println("firstEven = " + firstEvenInteger(new Integer[]{1, 3, 8}) + " / " + firstEvenInteger(new Integer[]{1}));
        java.util.function.IntPredicate above10 = x -> x > 10;   // OptInt.filter takes an IntPredicate
        IO.println("transform = " + a.transform(x -> x * 10) + ", filter = " + a.filter(above10) + "/" + OptInt.some(50).filter(above10));
        IO.println("sq(3) = " + MathX.sq(3) + ", kb(4) = " + MathX.kb(4) + ", clamp = " + MathX.clamp(sum, 0, 10));
        MathX.log("done");

        IO.println("maybeUser = " + maybeUser + ", someUser = " + someUser);
        MyList<String> names = new MyList<>("a", "b", "c");   // → MyListString, varargs constructor
        MyList<int> numbers = new MyList<>(1, 2, 3);          // → MyListInt(int...)
        names.print();
        numbers.print();
        IO.println("sum = " + numbers.fold(0, Integer::sum) + ", squares = " + numbers.map(MathX::sq));
        Dict<int, String> byId = Dict.of(1, "one");           // → DictInt<String>
        byId.put(2, "two");
        IO.println("byId = " + byId + ", crc = " + Integer.toHexString(Checksums.crc32("hello".getBytes())));
        List<int> primes = new ArrayList<>();                  // → MyListInt primes = new MyListInt()
        primes.add(2);
        primes.add(3);
        Map<int, String> names2 = new HashMap<>();             // → DictInt<String>
        names2.put(2, "two");
        IO.println("primes = " + primes + " (" + primes.getClass().getSimpleName() + "), names2 = " + names2);

        MyList<Integer> myList4 = new MyList<>(1, 2, 3);
        myList4.print();

        MyList<Object> myList3 = new MyList<>(List.of("a", 1, 2.0));
        myList3.print();
    }
}
