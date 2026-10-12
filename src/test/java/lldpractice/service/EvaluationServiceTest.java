package lldpractice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import lldpractice.catalog.ProblemCatalog;
import lldpractice.catalog.SampleSubmissions;
import lldpractice.design.ExtractorRegistry;
import lldpractice.domain.Attempt;
import lldpractice.domain.AttemptStatus;
import lldpractice.domain.Finding;
import lldpractice.evaluation.EvaluationContext;
import lldpractice.evaluation.Evaluator;
import lldpractice.evaluation.LlmEvaluator;
import lldpractice.evaluation.MockLlmClient;
import lldpractice.evaluation.StructuralEvaluator;
import lldpractice.repository.AttemptRepository;
import lldpractice.repository.InMemoryAttemptRepository;
import lldpractice.repository.InMemoryProblemRepository;
import lldpractice.repository.ProblemRepository;

class EvaluationServiceTest {

    private final ProblemRepository problemRepository = new InMemoryProblemRepository(ProblemCatalog.all());

    /** An essential evaluator that throws on its first call only, then behaves normally. */
    private static final class FlakyEssentialEvaluator implements Evaluator {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String name() {
            return "Flaky essential";
        }

        @Override
        public boolean essential() {
            return true;
        }

        @Override
        public List<Finding> evaluate(EvaluationContext context) {
            if (calls.incrementAndGet() == 1) {
                throw new RuntimeException("simulated essential failure");
            }
            return List.of();
        }
    }

    @Test
    void essentialEvaluatorFailureFailsAttemptAndRetrySucceeds() {
        AttemptRepository attempts = new InMemoryAttemptRepository();
        FlakyEssentialEvaluator flaky = new FlakyEssentialEvaluator();
        EvaluationService evaluationService = new EvaluationService(
                problemRepository, attempts, ExtractorRegistry.standard(), List.of(flaky), Runnable::run);
        PracticeService practiceService = new PracticeService(problemRepository, attempts, evaluationService);

        Attempt attempt = practiceService.startAttempt("alice", "parking-lot", SampleSubmissions.strongStrategyBased());
        practiceService.submit(attempt.id());

        Attempt afterFirstRun = practiceService.getAttempt(attempt.id());
        assertEquals(AttemptStatus.FAILED, afterFirstRun.status());
        assertNotNull(afterFirstRun.failureReason());

        practiceService.retry(attempt.id());

        Attempt afterRetry = practiceService.getAttempt(attempt.id());
        assertEquals(AttemptStatus.COMPLETED, afterRetry.status());
    }

    @Test
    void llmTimeoutDegradesToPartiallyCompletedWithNote() {
        AttemptRepository attempts = new InMemoryAttemptRepository();
        Evaluator slowLlm = new LlmEvaluator(new SlowLlmClient(), Executors.newCachedThreadPool(),
                Duration.ofMillis(50), 1);
        EvaluationService evaluationService = new EvaluationService(
                problemRepository, attempts, ExtractorRegistry.standard(),
                List.of(new StructuralEvaluator(), slowLlm), Runnable::run);
        PracticeService practiceService = new PracticeService(problemRepository, attempts, evaluationService);

        Attempt attempt = practiceService.startAttempt("alice", "parking-lot", SampleSubmissions.strongStrategyBased());
        practiceService.submit(attempt.id());

        Attempt result = practiceService.getAttempt(attempt.id());
        assertEquals(AttemptStatus.PARTIALLY_COMPLETED, result.status());
        assertTrue(result.feedback().degraded());
        assertFalse(result.feedback().notes().isEmpty());
    }

    private static final class SlowLlmClient implements lldpractice.evaluation.LlmClient {
        @Override
        public String complete(String prompt) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "{}";
        }

        @Override
        public String description() {
            return "slow";
        }

        @Override
        public boolean offline() {
            return true;
        }
    }

    @Test
    void concurrentEvaluateAsyncCallsOnlyRunEvaluationOnce() throws InterruptedException {
        AttemptRepository attempts = new InMemoryAttemptRepository();
        AtomicInteger evaluationRuns = new AtomicInteger();
        Evaluator countingEvaluator = new Evaluator() {
            @Override public String name() { return "counting"; }
            @Override public boolean essential() { return true; }
            @Override public List<Finding> evaluate(EvaluationContext context) {
                evaluationRuns.incrementAndGet();
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return List.of();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            EvaluationService evaluationService = new EvaluationService(
                    problemRepository, attempts, ExtractorRegistry.standard(), List.of(countingEvaluator), pool);
            PracticeService practiceService = new PracticeService(problemRepository, attempts, evaluationService);

            Attempt attempt = practiceService.startAttempt("alice", "parking-lot", SampleSubmissions.strongStrategyBased());
            attempt.transitionTo(AttemptStatus.SUBMITTED);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Runnable task = () -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                evaluationService.evaluateAsync(attempt);
            };
            Thread t1 = new Thread(task);
            Thread t2 = new Thread(task);
            t1.start();
            t2.start();
            ready.await();
            go.countDown();
            t1.join();
            t2.join();

            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(1, evaluationRuns.get());
            assertEquals(AttemptStatus.COMPLETED, attempt.status());
        } finally {
            pool.shutdownNow();
        }
    }
}
