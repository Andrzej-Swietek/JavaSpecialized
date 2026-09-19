package dev.specialize.processor.model;

/** The class that stands for {@code template<types>}. */
public sealed interface Specialization permits GeneratedSpecialization, ExplicitSpecialization {

    String qualified();

    Template template();

    TargetTuple type();
}
