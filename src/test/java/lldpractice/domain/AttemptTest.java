package lldpractice.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import lldpractice.domain.exceptions.InvalidTransitionException;

class AttemptTest {

    private Attempt newDraftAttempt() {
        Submission submission = new Submission("a", "b", "c", "d", SubmissionFormat.JAVA);
        return new Attempt("attempt-1", "alice", "parking-lot", 1, submission);
    }

    @Test
    void doubleSubmitThrowsInvalidTransition() {
        Attempt attempt = newDraftAttempt();
        attempt.transitionTo(AttemptStatus.SUBMITTED);
        assertThrows(InvalidTransitionException.class, () -> attempt.transitionTo(AttemptStatus.SUBMITTED));
    }

    @Test
    void editAfterSubmitThrows() {
        Attempt attempt = newDraftAttempt();
        attempt.transitionTo(AttemptStatus.SUBMITTED);
        Submission edited = new Submission("x", "y", "z", "w", SubmissionFormat.JAVA);
        assertThrows(InvalidTransitionException.class, () -> attempt.updateSubmission(edited));
    }

    @Test
    void completingDirectlyFromDraftThrows() {
        Attempt attempt = newDraftAttempt();
        Feedback feedback = new Feedback(List.of(), java.util.Map.of(), 100, "ok", false, List.of());
        assertThrows(InvalidTransitionException.class, () -> attempt.completeWith(feedback, true));
    }

    @Test
    void retryIsAllowedFromFailedAndPartiallyCompleted() {
        Attempt failed = newDraftAttempt();
        failed.transitionTo(AttemptStatus.SUBMITTED);
        failed.transitionTo(AttemptStatus.EVALUATING);
        failed.failWith("boom");
        assertEquals(AttemptStatus.FAILED, failed.status());
        failed.transitionTo(AttemptStatus.EVALUATING); // retry

        Attempt partial = newDraftAttempt();
        partial.transitionTo(AttemptStatus.SUBMITTED);
        partial.transitionTo(AttemptStatus.EVALUATING);
        Feedback feedback = new Feedback(List.of(), java.util.Map.of(), 50, "partial", true, List.of("degraded"));
        partial.completeWith(feedback, false);
        assertEquals(AttemptStatus.PARTIALLY_COMPLETED, partial.status());
        partial.transitionTo(AttemptStatus.EVALUATING); // retry
    }

    @Test
    void concurrentTransitionsOnlyOneSucceeds() throws InterruptedException {
        Attempt attempt = newDraftAttempt();
        attempt.transitionTo(AttemptStatus.SUBMITTED);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Runnable attempt1 = () -> {
            ready.countDown();
            await(go);
            try {
                attempt.transitionTo(AttemptStatus.EVALUATING);
                successes.incrementAndGet();
            } catch (InvalidTransitionException e) {
                failures.incrementAndGet();
            }
        };

        Thread t1 = new Thread(attempt1);
        Thread t2 = new Thread(attempt1);
        t1.start();
        t2.start();
        ready.await();
        go.countDown();
        t1.join();
        t2.join();

        assertEquals(1, successes.get());
        assertEquals(1, failures.get());
        assertEquals(AttemptStatus.EVALUATING, attempt.status());
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
