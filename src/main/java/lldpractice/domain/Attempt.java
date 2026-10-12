package lldpractice.domain;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import lldpractice.domain.exceptions.InvalidTransitionException;

/**
 * A single learner's attempt at a {@link Problem}: their {@link Submission}, its lifecycle
 * {@link AttemptStatus}, and the {@link Feedback} once evaluation completes.
 *
 * <p>Status transitions go through {@link #transitionTo(AttemptStatus)}, which validates the
 * move against {@link AttemptStatus#canTransitionTo(AttemptStatus)} and is safe to call from
 * concurrent threads (the evaluation pipeline runs on a background executor while a learner
 * may be polling the same attempt).
 */
public final class Attempt {

    private final String id;
    private final String learnerName;
    private final String problemId;
    private final int attemptNumber;
    private final Instant createdAt;

    private final AtomicReference<AttemptStatus> status = new AtomicReference<>(AttemptStatus.DRAFT);
    private volatile Submission submission;
    private volatile Feedback feedback;
    private volatile String failureReason;
    private volatile Instant submittedAt;
    private volatile Instant completedAt;

    public Attempt(String id, String learnerName, String problemId, int attemptNumber, Submission submission) {
        this.id = id;
        this.learnerName = learnerName;
        this.problemId = problemId;
        this.attemptNumber = attemptNumber;
        this.submission = submission;
        this.createdAt = Instant.now();
    }

    private Attempt(String id, String learnerName, String problemId, int attemptNumber, Instant createdAt,
                     Submission submission) {
        this.id = id;
        this.learnerName = learnerName;
        this.problemId = problemId;
        this.attemptNumber = attemptNumber;
        this.createdAt = createdAt;
        this.submission = submission;
    }

    /**
     * Rebuilds an {@link Attempt} exactly as it was persisted, bypassing transition validation.
     * Used only by storage implementations (e.g. a JSON-file repository) when reading an attempt
     * back from disk; application code should never call this directly.
     */
    public static Attempt restore(String id, String learnerName, String problemId, int attemptNumber,
                                   Instant createdAt, Submission submission, AttemptStatus status,
                                   Feedback feedback, String failureReason, Instant submittedAt, Instant completedAt) {
        Attempt attempt = new Attempt(id, learnerName, problemId, attemptNumber, createdAt, submission);
        attempt.status.set(status);
        attempt.feedback = feedback;
        attempt.failureReason = failureReason;
        attempt.submittedAt = submittedAt;
        attempt.completedAt = completedAt;
        return attempt;
    }

    public String id() {
        return id;
    }

    public String learnerName() {
        return learnerName;
    }

    public String problemId() {
        return problemId;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public AttemptStatus status() {
        return status.get();
    }

    public Submission submission() {
        return submission;
    }

    public Feedback feedback() {
        return feedback;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    /** Replaces the submission while still a draft. Throws if the attempt has already been submitted. */
    public synchronized void updateSubmission(Submission newSubmission) {
        if (status.get() != AttemptStatus.DRAFT) {
            throw new InvalidTransitionException(status.get(), AttemptStatus.DRAFT);
        }
        this.submission = newSubmission;
    }

    /**
     * Atomically moves the attempt to {@code next}, throwing {@link InvalidTransitionException}
     * if the current status does not allow it. Returns the previous status.
     */
    public AttemptStatus transitionTo(AttemptStatus next) {
        while (true) {
            AttemptStatus current = status.get();
            if (!current.canTransitionTo(next)) {
                throw new InvalidTransitionException(current, next);
            }
            if (status.compareAndSet(current, next)) {
                if (next == AttemptStatus.SUBMITTED) {
                    submittedAt = Instant.now();
                }
                if (next == AttemptStatus.COMPLETED || next == AttemptStatus.PARTIALLY_COMPLETED
                        || next == AttemptStatus.FAILED) {
                    completedAt = Instant.now();
                }
                return current;
            }
            // Another thread changed status concurrently; re-check against the new state.
        }
    }

    public void completeWith(Feedback result, boolean fullyPassed) {
        this.feedback = result;
        transitionTo(fullyPassed ? AttemptStatus.COMPLETED : AttemptStatus.PARTIALLY_COMPLETED);
    }

    public void failWith(String reason) {
        this.failureReason = reason;
        transitionTo(AttemptStatus.FAILED);
    }
}
