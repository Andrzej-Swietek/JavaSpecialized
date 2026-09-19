package dev.specialize.processor.inline;


/** Unique names for the locals an expansion introduces, numbered per compilation unit so builds are reproducible. */
final class LocalNames {
    private int next;

    /** Starts a new numbered group; every name of one expansion shares its number. */
    int nextGroup() {
        return ++next;
    }
}
