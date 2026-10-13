package lldpractice.catalog;

import static lldpractice.testsupport.JavaCompileAssert.assertCompiles;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lldpractice.design.DesignModel;
import lldpractice.design.JavaDesignExtractor;
import lldpractice.domain.Feedback;
import lldpractice.domain.Finding;
import lldpractice.domain.Problem;
import lldpractice.domain.Severity;
import lldpractice.domain.Submission;
import lldpractice.evaluation.EvaluationContext;
import lldpractice.evaluation.EvaluationPipeline.PipelineResult;
import lldpractice.evaluation.FeedbackBuilder;
import lldpractice.evaluation.StructuralEvaluator;

/**
 * The same weak/strong contrast as {@link SampleSubmissionsTest}, repeated for Vending Machine
 * instead of Parking Lot, so the evaluator's behaviour is demonstrated on more than one problem.
 */
class VendingMachineSampleSubmissionsTest {

    private final JavaDesignExtractor extractor = new JavaDesignExtractor();
    private final StructuralEvaluator evaluator = new StructuralEvaluator();
    private final FeedbackBuilder feedbackBuilder = new FeedbackBuilder();
    private final Problem problem = ProblemCatalog.vendingMachine();

    private Feedback evaluate(Submission submission) {
        DesignModel model = extractor.extract(submission);
        EvaluationContext context = new EvaluationContext(problem, submission, model);
        List<Finding> findings = evaluator.evaluate(context);
        return feedbackBuilder.build(problem, new PipelineResult(findings, false, List.of()));
    }

    @Test
    void weakDesignIsFlaggedForKnownSmells() {
        Feedback feedback = evaluate(SampleSubmissions.vendingWeak());
        assertTrue(hasFinding(feedback, Severity.MAJOR, "god class"));
        assertTrue(hasFinding(feedback, Severity.MAJOR, "instanceof"));
        assertTrue(hasFinding(feedback, Severity.MAJOR, "public mutable"));
        assertTrue(hasFinding(feedback, Severity.MAJOR, "no shared abstraction"));
    }

    @Test
    void strongDesignScoresAtLeast70() {
        Feedback feedback = evaluate(SampleSubmissions.vendingStrong());
        assertTrue(feedback.overallScore() >= 70, "expected >=70, got " + feedback.overallScore());
    }

    @Test
    void strongDesignUsesAPaymentStrategyInterfaceThatIsActuallyImplemented() {
        Feedback feedback = evaluate(SampleSubmissions.vendingStrong());
        assertTrue(hasFinding(feedback, Severity.POSITIVE, "interfaces/abstract classes are used"));
    }

    @Test
    void weakSampleCodeCompiles() throws Exception {
        assertCompiles(SampleSubmissions.vendingWeak().code());
    }

    @Test
    void strongSampleCodeCompiles() throws Exception {
        assertCompiles(SampleSubmissions.vendingStrong().code());
    }

    private boolean hasFinding(Feedback feedback, Severity severity, String titleContains) {
        return feedback.findings().stream().anyMatch(f -> f.severity() == severity
                && f.title().toLowerCase().contains(titleContains.toLowerCase()));
    }
}
