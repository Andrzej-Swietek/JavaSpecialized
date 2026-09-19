package bench;

import dev.specialize.Boxed;
import dev.specialize.examples.Opt;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Summing a million optionals: the specialized {@code OptInt} against the boxed generic class and {@code Optional}. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class OptBenchmark {
    private static final int N = 1_000_000;

    private Opt<int>[] specialized;
    private @Boxed Opt<Integer>[] boxed;
    private Optional<Integer>[] optionals;

    @Setup
    @SuppressWarnings("unchecked")
    public void setup() {
        specialized = new Opt[N];
        boxed = new Opt[N];
        optionals = new Optional[N];
        for (int i = 0; i < N; i++) {
            boolean present = i % 3 != 0;
            specialized[i] = present ? Opt.some(i) : Opt.<int>empty();
            boxed[i] = present ? Opt.some((Integer) i) : Opt.empty();   // the element type is @Boxed: the generic class
            optionals[i] = present ? Optional.of(i) : Optional.empty();
        }
    }

    @Benchmark
    public long optInt() {
        long sum = 0;
        for (Opt<int> o : specialized) {
            sum += o.getOrElse(() -> -1);
        }
        return sum;
    }

    @Benchmark
    public long optBoxed() {
        long sum = 0;
        for (@Boxed Opt<Integer> o : boxed) {
            sum += o.getOrElse(() -> -1);
        }
        return sum;
    }

    @Benchmark
    public long optional() {
        long sum = 0;
        for (Optional<Integer> o : optionals) {
            sum += o.orElse(-1);
        }
        return sum;
    }
}
