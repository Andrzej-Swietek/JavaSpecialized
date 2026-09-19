package dev.specialize.examples;

import dev.specialize.Prim;
import dev.specialize.Specialize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A small array-backed list; {@code MyList<int>} keeps an {@code int[]} and takes {@code int...}, {@code Function<T, R>}
 * becomes {@code IntFunction<R>}, {@code Consumer<T>} an {@code IntConsumer}. It also stands for the JDK list:
 * {@code List<int> xs = new ArrayList<>()} in client code is {@code MyListInt xs = new MyListInt()}.
 */
@Specialize(types = {int.class, long.class, double.class, String.class}, standsFor = {List.class, ArrayList.class})
public final class MyList<T> implements Iterable<T> {
    private static final int MIN_CAPACITY = 4;

    private T[] items;
    private int size;

    @SafeVarargs
    public MyList(T... initial) {
        items = Prim.newArray(Math.max(MIN_CAPACITY, initial.length));
        System.arraycopy(initial, 0, items, 0, initial.length);
        size = initial.length;
    }

    public static <T> MyList<T> empty() {
        return new MyList<>();
    }

    public void add(T item) {
        if (size == items.length) {
            items = Arrays.copyOf(items, size * 2);
        }
        items[size++] = item;
    }

    public T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
        return items[index];
    }

    public int size() {
        return size;
    }

    public boolean contains(T item) {
        for (int i = 0; i < size; i++) {
            if (Prim.eq(items[i], item)) {
                return true;
            }
        }
        return false;
    }

    public MyList<T> filter(Predicate<T> keep) {
        MyList<T> out = MyList.empty();
        for (int i = 0; i < size; i++) {
            if (keep.test(items[i])) {
                out.add(items[i]);
            }
        }
        return out;
    }

    public <R> MyList<R> map(Function<T, R> f) {
        MyList<R> out = MyList.empty();
        for (int i = 0; i < size; i++) {
            out.add(f.apply(items[i]));
        }
        return out;
    }

    public <A> A fold(A zero, BiFunction<A, T, A> step) {
        A acc = zero;
        for (int i = 0; i < size; i++) {
            acc = step.apply(acc, items[i]);
        }
        return acc;
    }

    public void each(Consumer<T> action) {
        for (int i = 0; i < size; i++) {
            action.accept(items[i]);
        }
    }

    public void print() {
        System.out.println(this);
    }

    @Override
    public java.util.Iterator<T> iterator() {
        return new java.util.Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
                return next < size;
            }

            @Override
            public T next() {
                if (next >= size) {
                    throw new NoSuchElementException();
                }
                return items[next++];
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            sb.append(i == 0 ? "" : ", ").append(Prim.str(items[i]));
        }
        return sb.append(']').toString();
    }
}
