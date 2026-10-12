package lldpractice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lldpractice.domain.Attempt;
import lldpractice.domain.AttemptStatus;
import lldpractice.domain.Feedback;
import lldpractice.domain.Finding;
import lldpractice.domain.FindingSource;
import lldpractice.domain.Severity;
import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;

/** Proves the Repository seam actually buys swappability: an attempt saved through one
 * {@link JsonFileAttemptRepository} instance is fully readable (including its lifecycle state
 * and feedback) by a brand new instance pointed at the same directory, with zero changes to
 * any calling code -- the same thing {@link PracticeServiceTest} exercises against the
 * in-memory implementation. */
class JsonFileAttemptRepositoryTest {

    @Test
    void attemptSurvivesBeingReloadedByANewRepositoryInstance(@TempDir Path dir) {
        JsonFileAttemptRepository first = new JsonFileAttemptRepository(dir);

        Submission submission = new Submission("assume", "outline", "code", "tradeoffs", SubmissionFormat.JAVA);
        Attempt attempt = new Attempt("a1", "alice", "parking-lot", 1, submission);
        attempt.transitionTo(AttemptStatus.SUBMITTED);
        attempt.transitionTo(AttemptStatus.EVALUATING);
        Finding finding = new Finding("abstraction", Severity.MAJOR, "title", "evidence", "suggestion",
                List.of("alt1"), FindingSource.DETERMINISTIC);
        Feedback feedback = new Feedback(List.of(finding), Map.of("abstraction", 80), 80, "summary", false, List.of());
        attempt.completeWith(feedback, true);

        first.save(attempt);

        JsonFileAttemptRepository second = new JsonFileAttemptRepository(dir);
        Attempt reloaded = second.findById("a1").orElseThrow();

        assertEquals(AttemptStatus.COMPLETED, reloaded.status());
        assertEquals("alice", reloaded.learnerName());
        assertEquals("code", reloaded.submission().code());
        assertEquals(80, reloaded.feedback().overallScore());
        assertEquals(1, reloaded.feedback().findings().size());
        assertEquals("title", reloaded.feedback().findings().get(0).title());
        assertEquals(List.of("alt1"), reloaded.feedback().findings().get(0).alternatives());
        assertTrue(second.findByLearnerAndProblem("alice", "parking-lot").size() == 1);
    }
}
