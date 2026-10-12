package lldpractice.evaluation;

/** Thrown when {@link LlmEvaluator} cannot produce a usable result after all retries (timeout,
 * network failure, or a response that never parsed as valid JSON). Caught by
 * {@link EvaluationPipeline}, which degrades to deterministic-only feedback rather than failing
 * the whole attempt, since this evaluator is non-essential. */
public class LlmEvaluationException extends RuntimeException {
    public LlmEvaluationException(String message) {
        super(message);
    }

    public LlmEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
