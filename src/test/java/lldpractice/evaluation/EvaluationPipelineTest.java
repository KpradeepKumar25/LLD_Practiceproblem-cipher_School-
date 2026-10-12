package lldpractice.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lldpractice.catalog.ProblemCatalog;
import lldpractice.design.DesignModel;
import lldpractice.domain.Finding;
import lldpractice.domain.FindingSource;
import lldpractice.domain.Problem;
import lldpractice.domain.Severity;
import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;
import lldpractice.evaluation.EvaluationPipeline.PipelineResult;

class EvaluationPipelineTest {

    private final Problem problem = ProblemCatalog.parkingLot();

    private EvaluationContext newContext() {
        Submission submission = new Submission("a", "b", "c", "d", SubmissionFormat.JAVA);
        return new EvaluationContext(problem, submission, DesignModel.empty());
    }

    private static final class ThrowingEvaluator implements Evaluator {
        private final boolean essential;

        ThrowingEvaluator(boolean essential) {
            this.essential = essential;
        }

        @Override
        public String name() {
            return "Throwing evaluator";
        }

        @Override
        public boolean essential() {
            return essential;
        }

        @Override
        public List<Finding> evaluate(EvaluationContext context) {
            throw new RuntimeException("boom");
        }
    }

    private static final class StubEvaluator implements Evaluator {
        private final List<Finding> findings;

        StubEvaluator(List<Finding> findings) {
            this.findings = findings;
        }

        @Override
        public String name() {
            return "Stub evaluator";
        }

        @Override
        public boolean essential() {
            return true;
        }

        @Override
        public List<Finding> evaluate(EvaluationContext context) {
            return findings;
        }
    }

    @Test
    void essentialEvaluatorThrowingFailsTheWholeRun() {
        EvaluationPipeline pipeline = new EvaluationPipeline(List.of(new ThrowingEvaluator(true)));
        assertThrows(EssentialEvaluatorFailedException.class, () -> pipeline.run(newContext()));
    }

    @Test
    void nonEssentialEvaluatorThrowingDegradesInsteadOfFailing() {
        Finding deterministic = new Finding("requirement_coverage", Severity.MAJOR, "missing thing", "ev", "sug",
                List.of(), FindingSource.DETERMINISTIC);
        EvaluationPipeline pipeline = new EvaluationPipeline(
                List.of(new StubEvaluator(List.of(deterministic)), new ThrowingEvaluator(false)));

        PipelineResult result = pipeline.run(newContext());

        assertTrue(result.degraded());
        assertEquals(1, result.findings().size());
        assertFalse(result.notes().isEmpty());
    }

    @Test
    void laterEvaluatorsSeePriorFindingsViaContext() {
        Finding deterministic = new Finding("abstraction", Severity.MINOR, "note", "", "", List.of(), FindingSource.DETERMINISTIC);
        EvaluationContext context = newContext();

        final List<Finding>[] seen = new List[1];
        Evaluator observer = new Evaluator() {
            @Override public String name() { return "observer"; }
            @Override public boolean essential() { return true; }
            @Override public List<Finding> evaluate(EvaluationContext ctx) {
                seen[0] = ctx.priorFindings();
                return List.of();
            }
        };

        new EvaluationPipeline(List.of(new StubEvaluator(List.of(deterministic)), observer)).run(context);

        assertEquals(1, seen[0].size());
        assertEquals("note", seen[0].get(0).title());
    }
}
