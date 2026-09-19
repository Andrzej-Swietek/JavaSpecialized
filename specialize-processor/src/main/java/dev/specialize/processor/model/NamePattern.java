package dev.specialize.processor.model;

/** {@code {Name}{Type}} → {@code OptInt}. */
public final class NamePattern {
    private NamePattern() {
    }

    public static String expand(String pattern, String name, String suffix) {
        return pattern.replace("{Name}", name).replace("{Type}", suffix);
    }
}
