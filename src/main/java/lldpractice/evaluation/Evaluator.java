package lldpractice.evaluation;

import java.util.List;

import lldpractice.domain.Finding;

/**
 * One pluggable scoring strategy (Strategy pattern). {@link EvaluationPipeline} runs a list of
 * evaluators in order against the same {@link EvaluationContext}; {@link #essential()}
 * distinguishes evaluators whose failure should fail the whole attempt from ones (like an LLM
 * call) whose failure should simply degrade the feedback to deterministic-only.
 */
public interface Evaluator {

    /** Shown in the UI next to each finding this evaluator produces. */
    String name();

    /** If true, a thrown exception here fails the attempt. If false, it only degrades feedback. */
    boolean essential();

    /** Produces findings for the submission described by {@code context}. May throw; the
     * pipeline decides what that means based on {@link #essential()}. */
    List<Finding> evaluate(EvaluationContext context);
}
