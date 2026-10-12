package lldpractice;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lldpractice.catalog.ProblemCatalog;
import lldpractice.design.ExtractorRegistry;
import lldpractice.evaluation.AnthropicLlmClient;
import lldpractice.evaluation.Evaluator;
import lldpractice.evaluation.LlmClient;
import lldpractice.evaluation.LlmEvaluator;
import lldpractice.evaluation.MockLlmClient;
import lldpractice.evaluation.StructuralEvaluator;
import lldpractice.repository.AttemptRepository;
import lldpractice.repository.InMemoryAttemptRepository;
import lldpractice.repository.InMemoryProblemRepository;
import lldpractice.repository.ProblemRepository;
import lldpractice.service.EvaluationService;
import lldpractice.service.PracticeService;
import lldpractice.web.WebServer;

/**
 * Wires every piece together and starts the server. This is the only place that knows about
 * concrete implementations (which repository, which LLM client); everything else in the app
 * depends on interfaces, which is what makes this class the one to touch when swapping one in.
 */
public final class App {

    public static void main(String[] args) throws IOException {
        int port = parsePort(System.getenv("PORT"), 8787);

        ProblemRepository problemRepository = new InMemoryProblemRepository(ProblemCatalog.all());
        AttemptRepository attemptRepository = new InMemoryAttemptRepository();
        ExtractorRegistry extractors = ExtractorRegistry.standard();

        LlmClient llmClient = buildLlmClient();
        ExecutorService llmExecutor = Executors.newCachedThreadPool();
        List<Evaluator> evaluators = List.of(
                new StructuralEvaluator(),
                new LlmEvaluator(llmClient, llmExecutor, Duration.ofSeconds(20), 2));

        ExecutorService evaluationExecutor = Executors.newFixedThreadPool(4);
        EvaluationService evaluationService = new EvaluationService(
                problemRepository, attemptRepository, extractors, evaluators, evaluationExecutor);
        PracticeService practiceService = new PracticeService(problemRepository, attemptRepository, evaluationService);

        WebServer server = new WebServer(port, practiceService);
        server.start();

        System.out.println("LLD Practice Platform running at http://localhost:" + port);
        System.out.println("AI reviewer: " + llmClient.description());
        System.out.println("Press Ctrl+C to stop.");
    }

    private static LlmClient buildLlmClient() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return new MockLlmClient();
        }
        return AnthropicLlmClient.fromEnvironment(apiKey);
    }

    private static int parsePort(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
