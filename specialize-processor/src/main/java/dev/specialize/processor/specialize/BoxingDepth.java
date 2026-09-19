package dev.specialize.processor.specialize;

import java.util.function.Supplier;

/**
 * Where a substituted variable sits: at a value position ({@code T x}) it becomes the primitive, inside a type
 * argument ({@code List<T>}) the box, marked as "was primitive" when the enclosing type is itself a template.
 */
final class BoxingDepth {
    private int depth;
    private boolean marking;

    boolean valuePosition() {
        return depth == 0;
    }

    boolean marking() {
        return marking;
    }

    /** Runs {@code action} one type-argument level deeper, marking primitives when {@code mark}. */
    <R> R boxed(boolean mark, Supplier<R> action) {
        depth++;
        boolean saved = marking;
        marking = mark;
        try {
            return action.get();
        } finally {
            marking = saved;
            depth--;
        }
    }

    /** Runs {@code action} back at value depth: an array's element type is a value. */
    <R> R atValue(Supplier<R> action) {
        int saved = depth;
        depth = 0;
        try {
            return action.get();
        } finally {
            depth = saved;
        }
    }
}
