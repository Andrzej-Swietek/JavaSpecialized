package dev.specialize;

/** What a boxed type argument such as {@code Opt<Integer>} means in client code; see {@link Specialize#boxedArguments()}. */
public enum BoxedArguments {
    /** {@code Opt<int>} is the specialization, {@code Opt<Integer>} stays the generic class (like {@code int[]} vs {@code Integer[]}). */
    KEEP,
    /** The default: {@code Opt<Integer>} is rewritten to the specialization as well; {@code @Boxed} opts a single use out. */
    SPECIALIZE
}
