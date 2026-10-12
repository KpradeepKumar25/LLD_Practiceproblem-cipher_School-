package lldpractice.evaluation;

import java.util.ArrayList;
import java.util.List;

import lldpractice.design.DesignModel;
import lldpractice.domain.Finding;
import lldpractice.domain.Problem;
import lldpractice.domain.Submission;

/**
 * Everything an {@link Evaluator} needs to judge one submission, plus the running list of
 * findings contributed so far by earlier evaluators in the {@link EvaluationPipeline}. Later
 * evaluators (the LLM) read {@link #priorFindings()} so they can avoid repeating what a
 * deterministic evaluator already said.
 */
public final class EvaluationContext {

    private final Problem problem;
    private final Submission submission;
    private final DesignModel designModel;
    private final List<Finding> priorFindings = new ArrayList<>();

    public EvaluationContext(Problem problem, Submission submission, DesignModel designModel) {
        this.problem = problem;
        this.submission = submission;
        this.designModel = designModel;
    }

    public Problem problem() {
        return problem;
    }

    public Submission submission() {
        return submission;
    }

    public DesignModel designModel() {
        return designModel;
    }

    public List<Finding> priorFindings() {
        return List.copyOf(priorFindings);
    }

    void addFindings(List<Finding> findings) {
        priorFindings.addAll(findings);
    }
}
