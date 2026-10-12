package lldpractice.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import lldpractice.catalog.ProblemCatalog;
import lldpractice.domain.Feedback;
import lldpractice.domain.Finding;
import lldpractice.domain.FindingSource;
import lldpractice.domain.Problem;
import lldpractice.domain.Severity;
import lldpractice.evaluation.EvaluationPipeline.PipelineResult;

class FeedbackBuilderTest {

    private final Problem problem = ProblemCatalog.parkingLot();
    private final FeedbackBuilder builder = new FeedbackBuilder();

    @Test
    void criterionScoresNeverGoBelowZeroEvenWithManyCriticalFindings() {
        List<Finding> findings = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            findings.add(new Finding("requirement_coverage", Severity.CRITICAL, "issue " + i, "ev", "sug",
                    List.of(), FindingSource.DETERMINISTIC));
        }
        Feedback feedback = builder.build(problem, new PipelineResult(findings, false, List.of()));
        assertEquals(0, feedback.criterionScores().get("requirement_coverage"));
        assertTrue(feedback.overallScore() >= 0 && feedback.overallScore() <= 100);
    }

    @Test
    void overallScoreIs100WhenThereAreNoFindings() {
        Feedback feedback = builder.build(problem, new PipelineResult(List.of(), false, List.of()));
        assertEquals(100, feedback.overallScore());
        for (int score : feedback.criterionScores().values()) {
            assertEquals(100, score);
        }
    }

    @Test
    void findingsAreOrderedMostSevereFirst() {
        List<Finding> findings = List.of(
                new Finding("reasoning", Severity.INFO, "info", "", "", List.of(), FindingSource.DETERMINISTIC),
                new Finding("reasoning", Severity.CRITICAL, "critical", "", "", List.of(), FindingSource.DETERMINISTIC),
                new Finding("reasoning", Severity.MINOR, "minor", "", "", List.of(), FindingSource.DETERMINISTIC)
        );
        Feedback feedback = builder.build(problem, new PipelineResult(findings, false, List.of()));
        assertEquals(Severity.CRITICAL, feedback.findings().get(0).severity());
        assertEquals(Severity.MINOR, feedback.findings().get(1).severity());
        assertEquals(Severity.INFO, feedback.findings().get(2).severity());
    }

    @Test
    void degradedFlagAndNotesArePassedThrough() {
        Feedback feedback = builder.build(problem, new PipelineResult(List.of(), true, List.of("AI review timed out")));
        assertTrue(feedback.degraded());
        assertEquals(List.of("AI review timed out"), feedback.notes());
    }
}
