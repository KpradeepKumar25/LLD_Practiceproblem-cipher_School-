package lldpractice.evaluation;

import java.util.ArrayList;
import java.util.List;

import lldpractice.domain.Finding;

/**
 * Composite that runs a fixed list of {@link Evaluator}s against one {@link EvaluationContext},
 * in order, so later evaluators (the LLM) can see what earlier ones (structural rules) already
 * found via {@link EvaluationContext#priorFindings()}.
 *
 * <p>An essential evaluator throwing aborts the whole run via
 * {@link EssentialEvaluatorFailedException}; a non-essential one throwing is caught and recorded
 * as a degraded-mode note instead, so one flaky dependency (the LLM) never fails an attempt that
 * deterministic rules could already evaluate.
 */
public final class EvaluationPipeline {

    private final List<Evaluator> evaluators;

    public EvaluationPipeline(List<Evaluator> evaluators) {
        this.evaluators = List.copyOf(evaluators);
    }

    public PipelineResult run(EvaluationContext context) {
        List<Finding> all = new ArrayList<>();
        boolean degraded = false;
        List<String> notes = new ArrayList<>();

        for (Evaluator evaluator : evaluators) {
            try {
                List<Finding> findings = evaluator.evaluate(context);
                all.addAll(findings);
                context.addFindings(findings);
            } catch (RuntimeException e) {
                if (evaluator.essential()) {
                    throw new EssentialEvaluatorFailedException(
                            evaluator.name() + " failed: " + e.getMessage(), e);
                }
                degraded = true;
                notes.add(evaluator.name() + " could not complete (" + e.getMessage()
                        + "); showing rule-based feedback only.");
            }
        }
        return new PipelineResult(all, degraded, notes);
    }

    public record PipelineResult(List<Finding> findings, boolean degraded, List<String> notes) {
    }
}
