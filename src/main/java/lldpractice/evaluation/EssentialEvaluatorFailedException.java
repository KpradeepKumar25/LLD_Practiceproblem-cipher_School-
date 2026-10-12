package lldpractice.evaluation;

/** Thrown by {@link EvaluationPipeline} when an {@link Evaluator#essential()} evaluator throws.
 * Unlike a non-essential failure, this fails the whole attempt: deterministic structural checks
 * are the floor the platform guarantees, so if they can't run, there is nothing trustworthy to show. */
public class EssentialEvaluatorFailedException extends RuntimeException {
    public EssentialEvaluatorFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
