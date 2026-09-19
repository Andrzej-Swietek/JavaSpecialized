package bench;

import dev.specialize.examples.Vec;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** {@code Vec.sumOf(data, x -> x * 2)} inlined with the lambda applied, against the same loop through an interface call. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class InlineBenchmark {
    private int[] data;

    @Setup
    public void setup() {
        data = new int[100_000];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
    }

    static int sumOfReference(int[] xs, IntUnaryOperator f) {
        int s = 0;
        for (int i = 0; i < xs.length; i++) {
            s += f.applyAsInt(xs[i]);
        }
        return s;
    }

    @Benchmark
    public int inlinedLambda() {
        return Vec.sumOf(data, x -> x * 2);
    }

    @Benchmark
    public int interfaceCall() {
        return sumOfReference(data, x -> x * 2);
    }
}
