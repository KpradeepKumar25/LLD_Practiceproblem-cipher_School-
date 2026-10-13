package lldpractice.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static lldpractice.testsupport.JavaCompileAssert.assertCompiles;

import java.util.List;

import org.junit.jupiter.api.Test;

import lldpractice.design.DesignModel;
import lldpractice.design.JavaDesignExtractor;
import lldpractice.domain.Feedback;
import lldpractice.domain.Finding;
import lldpractice.domain.Problem;
import lldpractice.domain.Severity;
import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;
import lldpractice.evaluation.EvaluationContext;
import lldpractice.evaluation.EvaluationPipeline.PipelineResult;
import lldpractice.evaluation.FeedbackBuilder;
import lldpractice.evaluation.StructuralEvaluator;

/**
 * Validates the three hand-written Parking Lot samples against the real evaluator: the weak one
 * must trigger specific smells, and both strong ones (different vocabulary, different structure)
 * must still score well, proving the platform doesn't anchor on one model answer. See
 * {@link VendingMachineSampleSubmissionsTest} for the same contrast on a second problem.
 */
class SampleSubmissionsTest {

    private final JavaDesignExtractor extractor = new JavaDesignExtractor();
    private final StructuralEvaluator evaluator = new StructuralEvaluator();
    private final FeedbackBuilder feedbackBuilder = new FeedbackBuilder();
    private final Problem problem = ProblemCatalog.parkingLot();

    private Feedback evaluate(Submission submission) {
        DesignModel model = extractor.extract(submission);
        EvaluationContext context = new EvaluationContext(problem, submission, model);
        List<Finding> findings = evaluator.evaluate(context);
        return feedbackBuilder.build(problem, new PipelineResult(findings, false, List.of()));
    }

    @Test
    void weakDesignIsFlaggedForKnownSmells() {
        Feedback feedback = evaluate(SampleSubmissions.weak());
        assertTrue(hasFinding(feedback, Severity.MAJOR, "god class"));
        assertTrue(hasFinding(feedback, Severity.MAJOR, "instanceof"));
        assertTrue(hasFinding(feedback, Severity.MAJOR, "public mutable"));
    }

    @Test
    void emptyOrGarbageSubmissionScoresLowAndIsntFalselyPraised() {
        // Regression test: a near-empty class used to score 74/100 because four of the six
        // criteria had nothing to flag it on (no god class, no instanceof chain, no public
        // field, no unclosed-brace finding surfaced) and defaulted to a clean 100 each.
        Submission garbage = new Submission("solution Noo", "solution Noo", "class Garbage{", "solution Noo",
                SubmissionFormat.JAVA);
        Feedback feedback = evaluate(garbage);

        assertTrue(feedback.overallScore() < 60, "expected a clearly low score, got " + feedback.overallScore());
        assertTrue(hasFinding(feedback, Severity.CRITICAL, "could not be fully parsed"));
        assertTrue(hasFinding(feedback, Severity.MAJOR, "empty class"));
        assertTrue(feedback.findings().stream().noneMatch(f -> f.severity() == Severity.POSITIVE
                        && (f.criterionId().equals("extensibility") || f.criterionId().equals("encapsulation"))),
                "an empty submission shouldn't be praised for avoiding smells it has no content to exhibit");
    }

    @Test
    void pureGibberishWithNoJavaSyntaxAlsoScoresLow() {
        // Regression test: unlike the unclosed-brace case above, text with no "class"/
        // "interface" keyword at all produces zero parsed types and zero parser warnings,
        // which used to let abstraction/responsibility default to a clean 100 since their
        // checks were guarded by "only run if there's at least one type".
        Submission gibberish = new Submission("agdgadhgwdyygwdywg", "agdgadhgwdyygwdywg",
                "agdgadhgwdyygwdywg", "agdgadhgwdyygwdywg", SubmissionFormat.JAVA);
        Feedback feedback = evaluate(gibberish);

        assertTrue(feedback.overallScore() < 60, "expected a clearly low score, got " + feedback.overallScore());
        assertTrue(hasFinding(feedback, Severity.CRITICAL, "no classes, interfaces, enums or records"));
        assertEquals(4, feedback.findings().stream()
                .filter(f -> f.severity() == Severity.CRITICAL).count(),
                "abstraction, responsibility, extensibility and encapsulation should each take a critical hit");
    }

    @Test
    void strongStrategyBasedDesignScoresAtLeast70() {
        Feedback feedback = evaluate(SampleSubmissions.strongStrategyBased());
        assertTrue(feedback.overallScore() >= 70, "expected >=70, got " + feedback.overallScore());
    }

    @Test
    void strongRecordBasedDesignScoresAtLeast70() {
        Feedback feedback = evaluate(SampleSubmissions.strongRecordBased());
        assertTrue(feedback.overallScore() >= 70, "expected >=70, got " + feedback.overallScore());
    }

    @Test
    void bothStrongDesignsUseDifferentVocabularyButBothPass() {
        // Strategy-based uses "Spot"/"ParkingTicket"; record-based uses "Bay"/"Receipt".
        // Different names, same required concepts -- both should be fully covered.
        Feedback a = evaluate(SampleSubmissions.strongStrategyBased());
        Feedback b = evaluate(SampleSubmissions.strongRecordBased());
        assertEquals(100, a.criterionScores().get("requirement_coverage"));
        assertEquals(100, b.criterionScores().get("requirement_coverage"));
    }

    @Test
    void weakSampleCodeCompiles() throws Exception {
        assertCompiles(SampleSubmissions.weak().code());
    }

    @Test
    void strongStrategyBasedSampleCodeCompiles() throws Exception {
        assertCompiles(SampleSubmissions.strongStrategyBased().code());
    }

    @Test
    void strongRecordBasedSampleCodeCompiles() throws Exception {
        assertCompiles(SampleSubmissions.strongRecordBased().code());
    }

    private boolean hasFinding(Feedback feedback, Severity severity, String titleContains) {
        return feedback.findings().stream().anyMatch(f -> f.severity() == severity
                && f.title().toLowerCase().contains(titleContains.toLowerCase()));
    }
}
