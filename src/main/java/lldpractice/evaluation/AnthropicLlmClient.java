package lldpractice.evaluation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import lldpractice.util.Json;

/**
 * Talks to the real Anthropic API via {@link HttpClient} (no SDK dependency). Used whenever
 * {@code ANTHROPIC_API_KEY} is set; otherwise {@link lldpractice.service.EvaluationService}
 * falls back to {@link MockLlmClient} so the platform still works with zero configuration.
 */
public final class AnthropicLlmClient implements LlmClient {

    private static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    private static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public AnthropicLlmClient(String apiKey, String model, Duration requestTimeout) {
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    }

    /** Builds a client from environment variables: {@code ANTHROPIC_API_KEY} (required) and the
     * optional {@code ANTHROPIC_MODEL} override. */
    public static AnthropicLlmClient fromEnvironment(String apiKey) {
        String model = System.getenv("ANTHROPIC_MODEL");
        return new AnthropicLlmClient(apiKey, model, Duration.ofSeconds(20));
    }

    @Override
    public String description() {
        return "Anthropic (" + model + ")";
    }

    @Override
    public boolean offline() {
        return false;
    }

    @Override
    public String complete(String prompt) {
        Map<String, Object> body = Json.object(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(Json.object("role", "user", "content", prompt))
        );
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(requestTimeout)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmCallException("Anthropic API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            Map<String, Object> json = Json.asObject(Json.parse(response.body()));
            List<Object> content = Json.asArray(json.get("content"));
            if (content.isEmpty()) {
                throw new LlmCallException("Anthropic API response had no content blocks");
            }
            Map<String, Object> firstBlock = Json.asObject(content.get(0));
            Object text = firstBlock.get("text");
            if (text == null) {
                throw new LlmCallException("Anthropic API response content block had no text");
            }
            return text.toString();
        } catch (IOException e) {
            throw new LlmCallException("Network error calling Anthropic API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCallException("Interrupted while calling Anthropic API", e);
        }
    }
}
