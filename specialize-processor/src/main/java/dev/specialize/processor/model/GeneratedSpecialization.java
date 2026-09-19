package dev.specialize.processor.model;

/**
 * Written by the processor (now, or when the template's module was compiled).
 */
public record GeneratedSpecialization(
        String qualified,
        Template template,
        TargetTuple type
) implements Specialization {
}
