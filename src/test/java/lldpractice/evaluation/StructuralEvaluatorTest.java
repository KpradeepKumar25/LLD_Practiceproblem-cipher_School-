package lldpractice.evaluation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lldpractice.catalog.ProblemCatalog;
import lldpractice.design.DesignModel;
import lldpractice.design.JavaDesignExtractor;
import lldpractice.domain.Finding;
import lldpractice.domain.Problem;
import lldpractice.domain.Severity;
import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;

/**
 * Unit tests for individual {@link StructuralEvaluator} rules, isolated from full sample
 * submissions (see {@code SampleSubmissionsTest} / {@code VendingMachineSampleSubmissionsTest}
 * for the end-to-end, whole-submission scoring tests).
 */
class StructuralEvaluatorTest {

    private final JavaDesignExtractor extractor = new JavaDesignExtractor();
    private final StructuralEvaluator evaluator = new StructuralEvaluator();
    private final Problem problem = ProblemCatalog.parkingLot();

    private List<Finding> findingsFor(String code) {
        Submission submission = new Submission(
                "Assumptions long enough to not itself trigger a reasoning finding here.",
                "outline", code,
                "Trade-offs long enough to not itself trigger a reasoning finding here.",
                SubmissionFormat.JAVA);
        DesignModel model = extractor.extract(submission);
        return evaluator.evaluate(new EvaluationContext(problem, submission, model));
    }

    private boolean has(List<Finding> findings, String criterionId, Severity severity, String titleContains) {
        return findings.stream().anyMatch(f -> f.criterionId().equals(criterionId) && f.severity() == severity
                && f.title().toLowerCase().contains(titleContains.toLowerCase()));
    }

    @Test
    void longMethodIsFlagged() {
        String longBody = "        int x = 0;\n".repeat(45);
        List<Finding> findings = findingsFor("""
                public class Thing {
                    public void doWork() {
                %s    }
                }
                """.formatted(longBody));
        assertTrue(has(findings, "responsibility", Severity.MAJOR, "long method"));
    }

    @Test
    void shortMethodIsNotFlaggedAsLong() {
        List<Finding> findings = findingsFor("""
                public class Thing {
                    public void doWork() {
                        int x = 1;
                    }
                }
                """);
        assertFalse(has(findings, "responsibility", Severity.MAJOR, "long method"));
    }

    @Test
    void highCouplingIsFlagged() {
        List<Finding> findings = findingsFor("""
                public class Orchestrator {
                    public void run(TypeA a, TypeB b, TypeC c, TypeD d, TypeE e, TypeF f, TypeG g) {
                    }
                }
                class TypeA {} class TypeB {} class TypeC {} class TypeD {}
                class TypeE {} class TypeF {} class TypeG {}
                """);
        assertTrue(has(findings, "extensibility", Severity.MAJOR, "high coupling"));
    }

    @Test
    void unusedInterfaceIsFlaggedMinor() {
        List<Finding> findings = findingsFor("""
                interface NeverImplemented {
                    void doThing();
                }
                public class Standalone {
                }
                """);
        assertTrue(has(findings, "abstraction", Severity.MINOR, "unused abstraction"));
    }

    @Test
    void interfaceWithAnImplementationIsNotFlaggedAsUnused() {
        List<Finding> findings = findingsFor("""
                interface Shape {
                    double area();
                }
                public class Circle implements Shape {
                    public double area() {
                        return 3.14;
                    }
                }
                """);
        assertFalse(has(findings, "abstraction", Severity.MINOR, "unused abstraction"));
    }

    @Test
    void genericManagerNameIsFlagged() {
        List<Finding> findings = findingsFor("""
                public class DataManager {
                    private int a;
                    private int b;
                    public void process() {}
                    public void handle() {}
                }
                """);
        assertTrue(has(findings, "responsibility", Severity.MINOR, "generic role name"));
    }

    @Test
    void repeatedEqualityChecksSuggestHardCodedVariation() {
        List<Finding> findings = findingsFor("""
                public class Checker {
                    public boolean check(String kind) {
                        if (kind.equals("a")) { return true; }
                        if (kind.equals("b")) { return true; }
                        if (kind.equals("c")) { return true; }
                        if (kind.equals("d")) { return true; }
                        if (kind.equals("e")) { return true; }
                        return false;
                    }
                }
                """);
        assertTrue(has(findings, "extensibility", Severity.MINOR, "hard-coded variation"));
    }

    @Test
    void thinAssumptionsAndTradeOffsAreFlaggedSeparately() {
        Submission submission = new Submission("short", "outline",
                "public class Thing {\n    private int x;\n}\n", "also short", SubmissionFormat.JAVA);
        DesignModel model = extractor.extract(submission);
        List<Finding> findings = evaluator.evaluate(new EvaluationContext(problem, submission, model));
        assertTrue(has(findings, "reasoning", Severity.MINOR, "assumptions"));
        assertTrue(has(findings, "reasoning", Severity.MINOR, "trade-offs"));
    }
}
