package lldpractice.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lldpractice.domain.Attempt;
import lldpractice.domain.AttemptComparison;
import lldpractice.domain.AttemptStatus;
import lldpractice.domain.Finding;
import lldpractice.domain.FindingSource;
import lldpractice.domain.Problem;
import lldpractice.domain.Severity;
import lldpractice.domain.Submission;
import lldpractice.domain.exceptions.NotFoundException;
import lldpractice.domain.exceptions.ValidationException;
import lldpractice.repository.AttemptRepository;
import lldpractice.repository.ProblemRepository;

/**
 * The learner-facing half of the practice loop: start an attempt, save a draft, submit it, ask
 * for a retry, and look at history. Everything about running the actual evaluation lives in
 * {@link EvaluationService} instead, so this class stays about the attempt's data and lifecycle,
 * not about how feedback gets computed.
 */
public final class PracticeService {

    private static final int MAX_SUBMISSION_LENGTH = 20_000;

    private final ProblemRepository problemRepository;
    private final AttemptRepository attemptRepository;
    private final EvaluationService evaluationService;

    public PracticeService(ProblemRepository problemRepository, AttemptRepository attemptRepository,
                            EvaluationService evaluationService) {
        this.problemRepository = problemRepository;
        this.attemptRepository = attemptRepository;
        this.evaluationService = evaluationService;
    }

    public Problem getProblem(String problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("No such problem: " + problemId));
    }

    public List<Problem> listProblems() {
        return problemRepository.findAll();
    }

    /** Starts a new draft attempt, optionally pre-filled (e.g. from a previous attempt, or a sample). */
    public Attempt startAttempt(String learnerName, String problemId, Submission prefill) {
        requireLearnerName(learnerName);
        getProblem(problemId); // throws NotFoundException if the problem doesn't exist
        int nextNumber = attemptRepository.highestAttemptNumber(learnerName, problemId) + 1;
        Submission initial = prefill == null
                ? new Submission("", "", "", "", lldpractice.domain.SubmissionFormat.JAVA)
                : prefill;
        Attempt attempt = new Attempt(UUID.randomUUID().toString(), learnerName, problemId, nextNumber, initial);
        return attemptRepository.save(attempt);
    }

    public void saveDraft(String attemptId, Submission submission) {
        Attempt attempt = getAttempt(attemptId);
        attempt.updateSubmission(submission);
        attemptRepository.save(attempt);
    }

    /** Validates and submits the attempt, then kicks off evaluation asynchronously. */
    public Attempt submit(String attemptId) {
        Attempt attempt = getAttempt(attemptId);
        validate(attempt.submission());
        attempt.transitionTo(AttemptStatus.SUBMITTED);
        attemptRepository.save(attempt);
        evaluationService.evaluateAsync(attempt);
        return attempt;
    }

    /** Re-runs evaluation on an attempt that previously FAILED or was PARTIALLY_COMPLETED. */
    public Attempt retry(String attemptId) {
        Attempt attempt = getAttempt(attemptId);
        evaluationService.evaluateAsync(attempt);
        return attempt;
    }

    public Attempt getAttempt(String attemptId) {
        return attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("No such attempt: " + attemptId));
    }

    /** Every attempt by this learner, most recent first. */
    public List<Attempt> history(String learnerName) {
        requireLearnerName(learnerName);
        return attemptRepository.findByLearner(learnerName);
    }

    /** This learner's attempts at one problem, in attempt-number order, for a score trend view. */
    public List<Attempt> historyForProblem(String learnerName, String problemId) {
        requireLearnerName(learnerName);
        return attemptRepository.findByLearnerAndProblem(learnerName, problemId);
    }

    /** Compares two completed/partially-completed attempts, newest vs. a previous one. */
    public AttemptComparison compare(String currentAttemptId, String previousAttemptId) {
        Attempt current = getAttempt(currentAttemptId);
        Attempt previous = getAttempt(previousAttemptId);
        if (current.feedback() == null || previous.feedback() == null) {
            throw new ValidationException("Both attempts must have feedback before they can be compared.");
        }

        List<Finding> currentDeterministic = deterministicFindings(current);
        List<Finding> previousDeterministic = deterministicFindings(previous);

        List<Finding> resolved = previousDeterministic.stream()
                .filter(f -> currentDeterministic.stream().noneMatch(c -> sameIssue(c, f)))
                .toList();
        List<Finding> remaining = currentDeterministic.stream()
                .filter(f -> previousDeterministic.stream().anyMatch(p -> sameIssue(p, f)))
                .toList();
        List<Finding> introduced = currentDeterministic.stream()
                .filter(f -> previousDeterministic.stream().noneMatch(p -> sameIssue(p, f)))
                .toList();

        Map<String, Integer> criterionDeltas = new LinkedHashMap<>();
        for (var entry : current.feedback().criterionScores().entrySet()) {
            int previousScore = previous.feedback().criterionScores().getOrDefault(entry.getKey(), 0);
            criterionDeltas.put(entry.getKey(), entry.getValue() - previousScore);
        }

        return new AttemptComparison(previous.id(), current.id(), resolved, remaining, introduced,
                previous.feedback().overallScore(), current.feedback().overallScore(), criterionDeltas);
    }

    /** Deterministic, non-positive findings: the reproducible "issues" that resolved/remaining/
     * introduced tracking in {@link #compare} is about. POSITIVE findings aren't issues, and LLM
     * findings aren't reproducible enough to safely call "resolved". */
    private List<Finding> deterministicFindings(Attempt attempt) {
        return attempt.feedback().findings().stream()
                .filter(f -> f.source() == FindingSource.DETERMINISTIC && f.severity() != Severity.POSITIVE)
                .toList();
    }

    private boolean sameIssue(Finding a, Finding b) {
        return a.criterionId().equals(b.criterionId()) && a.title().equals(b.title());
    }

    private void validate(Submission submission) {
        if (submission.isBlank()) {
            throw new ValidationException("Submission cannot be empty.");
        }
        if (submission.totalLength() > MAX_SUBMISSION_LENGTH) {
            throw new ValidationException(
                    "Submission is too large (max " + MAX_SUBMISSION_LENGTH + " characters).");
        }
    }

    private void requireLearnerName(String learnerName) {
        if (learnerName == null || learnerName.isBlank()) {
            throw new ValidationException("Learner name is required.");
        }
    }
}
