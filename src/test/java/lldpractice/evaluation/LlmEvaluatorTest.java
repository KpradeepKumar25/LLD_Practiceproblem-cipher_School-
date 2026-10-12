package lldpractice.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import lldpractice.catalog.ProblemCatalog;
import lldpractice.design.DesignModel;
import lldpractice.domain.Finding;
import lldpractice.domain.FindingSource;
import lldpractice.domain.Problem;
import lldpractice.domain.Severity;
import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;

class LlmEvaluatorTest {

    private final Problem problem = ProblemCatalog.parkingLot();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private EvaluationContext newContext() {
        Submission submission = new Submission("a", "b", "c", "d", SubmissionFormat.JAVA);
        return new EvaluationContext(problem, submission, DesignModel.empty());
    }

    /** A controllable {@link LlmClient} for tests: can delay, throw, or return scripted responses,
     * and always counts how many times it was called. */
    private static final class FakeLlmClient implements LlmClient {
        private final Deque<Object> script = new ArrayDeque<>(); // String response, or RuntimeException to throw
        private final AtomicInteger callCount = new AtomicInteger();
        private long delayMillis = 0;

        FakeLlmClient respond(String json) {
            script.add(json);
            return this;
        }

        FakeLlmClient fail() {
            script.add(new LlmCallException("simulated failure"));
            return this;
        }

        FakeLlmClient delay(long millis) {
            this.delayMillis = millis;
            return this;
        }

        int callCount() {
            return callCount.get();
        }

        @Override
        public String complete(String prompt) {
            callCount.incrementAndGet();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Object next = script.isEmpty() ? "{\"findings\":[]}" : script.poll();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (String) next;
        }

        @Override
        public String description() {
            return "fake";
        }

        @Override
        public boolean offline() {
            return true;
        }
    }

    @Test
    void timeoutThrowsAfterRetriesExhausted() {
        FakeLlmClient client = new FakeLlmClient().delay(500);
        LlmEvaluator evaluator = new LlmEvaluator(client, executor, Duration.ofMillis(100), 1);
        assertThrows(LlmEvaluationException.class, () -> evaluator.evaluate(newContext()));
    }

    @Test
    void invalidJsonIsRetriedMaxAttemptsTimes() {
        FakeLlmClient client = new FakeLlmClient().respond("not json at all").respond("still not json");
        LlmEvaluator evaluator = new LlmEvaluator(client, executor, Duration.ofSeconds(5), 2);
        assertThrows(LlmEvaluationException.class, () -> evaluator.evaluate(newContext()));
        assertEquals(2, client.callCount());
    }

    @Test
    void transientFailureThenSuccessReturnsFindingsFromSecondAttempt() {
        FakeLlmClient client = new FakeLlmClient().fail()
                .respond("{\"findings\":[{\"criterionId\":\"reasoning\",\"severity\":\"MINOR\",\"title\":\"ok\",\"evidence\":\"e\",\"suggestion\":\"s\",\"alternatives\":[]}]}");
        LlmEvaluator evaluator = new LlmEvaluator(client, executor, Duration.ofSeconds(5), 2);

        List<Finding> findings = evaluator.evaluate(newContext());

        assertEquals(1, findings.size());
        assertEquals("ok", findings.get(0).title());
        assertEquals(2, client.callCount());
    }

    @Test
    void unknownCriterionIdsAreDropped() {
        FakeLlmClient client = new FakeLlmClient().respond("""
                {"findings":[
                  {"criterionId":"bogus_id","severity":"MAJOR","title":"should be dropped","evidence":"","suggestion":"","alternatives":[]},
                  {"criterionId":"reasoning","severity":"MINOR","title":"should survive","evidence":"","suggestion":"","alternatives":[]}
                ]}
                """);
        LlmEvaluator evaluator = new LlmEvaluator(client, executor, Duration.ofSeconds(5), 1);

        List<Finding> findings = evaluator.evaluate(newContext());

        assertEquals(1, findings.size());
        assertEquals("should survive", findings.get(0).title());
    }

    @Test
    void criticalSeverityIsClampedToMajor() {
        FakeLlmClient client = new FakeLlmClient().respond("""
                {"findings":[
                  {"criterionId":"reasoning","severity":"CRITICAL","title":"too harsh","evidence":"","suggestion":"","alternatives":[]}
                ]}
                """);
        LlmEvaluator evaluator = new LlmEvaluator(client, executor, Duration.ofSeconds(5), 1);

        List<Finding> findings = evaluator.evaluate(newContext());

        assertEquals(1, findings.size());
        assertEquals(Severity.MAJOR, findings.get(0).severity());
        assertEquals(FindingSource.LLM, findings.get(0).source());
    }

    @Test
    void responseWrappedInMarkdownFenceIsStillParsed() {
        FakeLlmClient client = new FakeLlmClient().respond("""
                Sure, here is my review:
                ```json
                {"findings":[{"criterionId":"abstraction","severity":"INFO","title":"fenced","evidence":"","suggestion":"","alternatives":[]}]}
                ```
                """);
        LlmEvaluator evaluator = new LlmEvaluator(client, executor, Duration.ofSeconds(5), 1);

        List<Finding> findings = evaluator.evaluate(newContext());

        assertEquals(1, findings.size());
        assertEquals("fenced", findings.get(0).title());
    }

    @Test
    void essentialIsFalseAndNameMentionsClient() {
        FakeLlmClient client = new FakeLlmClient();
        LlmEvaluator evaluator = new LlmEvaluator(client, executor, Duration.ofSeconds(5), 1);
        assertTrue(!evaluator.essential());
        assertTrue(evaluator.name().contains("fake"));
    }
}
