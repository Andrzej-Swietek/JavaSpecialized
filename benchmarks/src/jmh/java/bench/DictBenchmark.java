package bench;

import dev.specialize.examples.Dict;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Lookups by primitive key: {@code DictInt<String>} (an {@code int[]} key table) against {@code HashMap<Integer, String>}. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class DictBenchmark {
    private static final int N = 10_000;

    private Dict<int, String> dict;
    private Map<Integer, String> hashMap;
    private int[] keys;

    @Setup
    public void setup() {
        dict = Dict.empty();
        hashMap = new HashMap<>();
        keys = new int[N];
        for (int i = 0; i < N; i++) {
            keys[i] = i * 7;
            dict.put(keys[i], "v" + i);
            hashMap.put(keys[i], "v" + i);
        }
    }

    @Benchmark
    public int dictInt() {
        int found = 0;
        for (int key : keys) {
            if (dict.get(key) != null) {
                found++;
            }
        }
        return found;
    }

    @Benchmark
    public int hashMap() {
        int found = 0;
        for (int key : keys) {
            if (hashMap.get(key) != null) {
                found++;
            }
        }
        return found;
    }
}
