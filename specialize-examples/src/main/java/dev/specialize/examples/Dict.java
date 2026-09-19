package dev.specialize.examples;

import dev.specialize.Prim;
import dev.specialize.Specialize;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A linear-probing map with a specialized key and a generic value: {@code Dict<int, String>} is {@code DictInt<String>}
 * with an {@code int[]} key table and no boxing on lookups; {@code Dict<User, X>} stays the generic class. Written as
 * {@code Map<int, String> m = new HashMap<>()} in client code it is the same {@code DictInt<String>}.
 */
@Specialize(standsFor = {Map.class, HashMap.class})
public final class Dict<@Specialize.Param(types = {int.class, long.class, String.class}) K, V> {
    private static final int INITIAL_CAPACITY = 8;

    private K[] keys = Prim.newArray(INITIAL_CAPACITY);
    private Object[] values = new Object[INITIAL_CAPACITY];
    private boolean[] used = new boolean[INITIAL_CAPACITY];
    private int size;

    public static <K, V> Dict<K, V> of(K key, V value) {
        Dict<K, V> dict = new Dict<>();
        dict.put(key, value);
        return dict;
    }

    public static <K, V> Dict<K, V> empty() {
        return new Dict<>();
    }

    public void put(K key, V value) {
        if (size * 2 >= keys.length) {
            grow();
        }
        int i = slot(key);
        if (!used[i]) {
            used[i] = true;
            keys[i] = key;
            size++;
        }
        values[i] = value;
    }

    /** The value, or {@code null} when the key is absent (like {@link Map#get}). */
    public V get(K key) {
        int i = slot(key);
        return used[i] ? castValue(values[i]) : null;
    }

    public V getOrElse(K key, Supplier<V> fallback) {
        int i = slot(key);
        return used[i] ? castValue(values[i]) : fallback.get();
    }

    public boolean containsKey(K key) {
        return used[slot(key)];
    }

    public int size() {
        return size;
    }

    public <R> Dict<K, R> mapValues(Function<V, R> f) {
        Dict<K, R> out = Dict.<K, R>empty();
        forEach((k, v) -> out.put(k, f.apply(v)));
        return out;
    }

    public void forEach(BiConsumer<K, V> action) {
        for (int i = 0; i < keys.length; i++) {
            if (used[i]) {
                action.accept(keys[i], castValue(values[i]));
            }
        }
    }

    public Class<?> keyType() {
        return Prim.<K>type();
    }

    private int slot(K key) {
        int i = (Prim.hash(key) & 0x7fffffff) % keys.length;
        while (used[i] && !Prim.eq(keys[i], key)) {
            i = (i + 1) % keys.length;
        }
        return i;
    }

    private void grow() {
        K[] oldKeys = keys;
        Object[] oldValues = values;
        boolean[] oldUsed = used;
        keys = Prim.newArray(oldKeys.length * 2);
        values = new Object[oldKeys.length * 2];
        used = new boolean[oldKeys.length * 2];
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldUsed[i]) {
                put(oldKeys[i], castValue(oldValues[i]));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> V castValue(Object value) {
        return (V) value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        forEach((k, v) -> sb.append(sb.length() == 1 ? "" : ", ").append(Prim.str(k)).append('=').append(v));
        return sb.append('}').toString();
    }
}
