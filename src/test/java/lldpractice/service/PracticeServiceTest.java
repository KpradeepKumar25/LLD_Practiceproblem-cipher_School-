package lldpractice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lldpractice.catalog.ProblemCatalog;
import lldpractice.catalog.SampleSubmissions;
import lldpractice.design.ExtractorRegistry;
import lldpractice.domain.Attempt;
import lldpractice.domain.AttemptComparison;
import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;
import lldpractice.domain.exceptions.NotFoundException;
import lldpractice.domain.exceptions.ValidationException;
import lldpractice.evaluation.Evaluator;
import lldpractice.evaluation.StructuralEvaluator;
import lldpractice.repository.AttemptRepository;
import lldpractice.repository.InMemoryAttemptRepository;
import lldpractice.repository.InMemoryProblemRepository;
import lldpractice.repository.ProblemRepository;

class PracticeServiceTest {

    private PracticeService practiceService;

    @BeforeEach
    void setUp() {
        ProblemRepository problemRepository = new InMemoryProblemRepository(ProblemCatalog.all());
        AttemptRepository attemptRepository = new InMemoryAttemptRepository();
        List<Evaluator> evaluators = List.of(new StructuralEvaluator());
        EvaluationService evaluationService = new EvaluationService(
                problemRepository, attemptRepository, ExtractorRegistry.standard(), evaluators, Runnable::run);
        practiceService = new PracticeService(problemRepository, attemptRepository, evaluationService);
    }

    @Test
    void attemptNumbersIncrementPerLearnerAndProblemButNotAcrossProblems() {
        Attempt a1 = practiceService.startAttempt("alice", "parking-lot", null);
        Attempt a2 = practiceService.startAttempt("alice", "parking-lot", null);
        Attempt b1 = practiceService.startAttempt("alice", "elevator-system", null);
        Attempt otherLearner = practiceService.startAttempt("bob", "parking-lot", null);

        assertEquals(1, a1.attemptNumber());
        assertEquals(2, a2.attemptNumber());
        assertEquals(1, b1.attemptNumber());
        assertEquals(1, otherLearner.attemptNumber());
    }

    @Test
    void historyIsOrderedMostRecentFirst() throws InterruptedException {
        Attempt first = practiceService.startAttempt("alice", "parking-lot", null);
        Thread.sleep(5);
        Attempt second = practiceService.startAttempt("alice", "elevator-system", null);

        List<Attempt> history = practiceService.history("alice");

        assertEquals(second.id(), history.get(0).id());
        assertEquals(first.id(), history.get(1).id());
    }

    @Test
    void submittingEmptySubmissionIsRejected() {
        Attempt attempt = practiceService.startAttempt("alice", "parking-lot",
                new Submission("", "", "", "", SubmissionFormat.JAVA));
        assertThrows(ValidationException.class, () -> practiceService.submit(attempt.id()));
    }

    @Test
    void submittingOversizedSubmissionIsRejected() {
        String huge = "x".repeat(25_000);
        Attempt attempt = practiceService.startAttempt("alice", "parking-lot",
                new Submission(huge, "", "", "", SubmissionFormat.JAVA));
        assertThrows(ValidationException.class, () -> practiceService.submit(attempt.id()));
    }

    @Test
    void unknownSubmissionFormatIsRejected() {
        assertThrows(ValidationException.class, () -> SubmissionFormat.parse("PLANTUML"));
    }

    @Test
    void unknownProblemIdIsRejected() {
        assertThrows(NotFoundException.class, () -> practiceService.startAttempt("alice", "does-not-exist", null));
    }

    @Test
    void compareReportsResolvedRemainingAndIntroducedIssues() {
        Attempt weak = practiceService.startAttempt("alice", "parking-lot", SampleSubmissions.weak());
        practiceService.submit(weak.id());

        Attempt strong = practiceService.startAttempt("alice", "parking-lot", SampleSubmissions.strongStrategyBased());
        practiceService.submit(strong.id());

        AttemptComparison comparison = practiceService.compare(strong.id(), weak.id());

        assertTrue(comparison.resolved().size() > 0, "expected some issues to be resolved");
        assertEquals(0, comparison.introduced().size(), "a strictly better design shouldn't introduce new issues");
        assertTrue(comparison.scoreDelta() > 0);
    }

    @Test
    void historyForProblemIsOrderedByAttemptNumber() {
        practiceService.startAttempt("alice", "parking-lot", null);
        practiceService.startAttempt("alice", "parking-lot", null);
        practiceService.startAttempt("alice", "parking-lot", null);

        List<Attempt> history = practiceService.historyForProblem("alice", "parking-lot");

        assertEquals(3, history.size());
        assertEquals(1, history.get(0).attemptNumber());
        assertEquals(3, history.get(2).attemptNumber());
    }
}
