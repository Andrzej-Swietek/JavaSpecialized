package dev.specialize.processor.consteval;

/** What running a field initializer at compile time produced. */
public sealed interface EvaluationResult {
    record Value(Object value) implements EvaluationResult {
    }

    record Failure(String message) implements EvaluationResult {
    }
}
