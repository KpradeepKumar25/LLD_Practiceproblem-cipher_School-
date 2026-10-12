package lldpractice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lldpractice.catalog.ProblemCatalog;
import lldpractice.design.ExtractorRegistry;
import lldpractice.evaluation.Evaluator;
import lldpractice.evaluation.LlmEvaluator;
import lldpractice.evaluation.MockLlmClient;
import lldpractice.evaluation.StructuralEvaluator;
import lldpractice.repository.InMemoryAttemptRepository;
import lldpractice.repository.InMemoryProblemRepository;
import lldpractice.service.EvaluationService;
import lldpractice.service.PracticeService;
import lldpractice.util.Json;

/**
 * Drives the real HTTP server end to end: prefill a sample, submit it, poll until evaluation
 * finishes, and check the JSON API reflects a completed attempt with a score. This is the one
 * test that exercises the whole stack together (web, services, evaluation pipeline, async
 * scheduling) rather than a single layer in isolation.
 */
class HttpSmokeTest {

    private WebServer server;
    private HttpClient http;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        var problemRepository = new InMemoryProblemRepository(ProblemCatalog.all());
        var attemptRepository = new InMemoryAttemptRepository();
        List<Evaluator> evaluators = List.of(
                new StructuralEvaluator(),
                new LlmEvaluator(new MockLlmClient(), Executors.newCachedThreadPool(), Duration.ofSeconds(5), 1));
        EvaluationService evaluationService = new EvaluationService(
                problemRepository, attemptRepository, ExtractorRegistry.standard(), evaluators,
                Executors.newFixedThreadPool(2));
        PracticeService practiceService = new PracticeService(problemRepository, attemptRepository, evaluationService);

        server = new WebServer(0, practiceService);
        server.start();
        baseUrl = "http://localhost:" + server.port();
        http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void prefillSubmitAndPollUntilCompleted() throws Exception {
        HttpResponse<Void> prefillResponse = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/problems/parking-lot/prefill/good-a")).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(302, prefillResponse.statusCode());
        String location = prefillResponse.headers().firstValue("Location").orElseThrow();
        String attemptId = location.substring(location.lastIndexOf('/') + 1);

        String formBody = "assumptions=" + enc("Each spot fits one size class.")
                + "&classOutline=" + enc("Vehicle interface, ParkingSpot, SpotAllocator, PricingStrategy, ParkingLot.")
                + "&code=" + enc("public class ParkingLot { private int size; public ParkingLot(int size) { this.size = size; } }")
                + "&tradeOffs=" + enc("Chose interfaces over inheritance for allocation and pricing so new policies don't touch ParkingLot.");

        HttpResponse<Void> submitResponse = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/attempts/" + attemptId + "/submit"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formBody))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(302, submitResponse.statusCode());

        String status = pollUntilTerminal(attemptId);

        assertTrue(status.equals("COMPLETED") || status.equals("PARTIALLY_COMPLETED"),
                "expected a terminal success status, got " + status);
    }

    @Test
    void unknownAttemptReturns404() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/attempts/does-not-exist")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    @Test
    void jsonApiListsProblems() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/problems")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        Object parsed = Json.parse(response.body());
        assertTrue(parsed instanceof List<?> list && list.size() == 3);
    }

    private String pollUntilTerminal(String attemptId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/attempts/" + attemptId)).build(),
                    HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            Map<String, Object> json = (Map<String, Object>) Json.parse(response.body());
            String status = (String) json.get("status");
            if (!status.equals("SUBMITTED") && !status.equals("EVALUATING")) {
                return status;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Attempt never reached a terminal status within the timeout");
    }

    private String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
