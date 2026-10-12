package lldpractice.evaluation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lldpractice.domain.Finding;
import lldpractice.domain.FindingSource;
import lldpractice.domain.Problem;
import lldpractice.domain.Severity;
import lldpractice.util.Json;

/**
 * The non-essential, advisory evaluator. It judges things a rule can't: whether responsibilities
 * are well placed, whether the stated trade-offs actually hold up, whether the chosen
 * abstractions make sense. Its output is never trusted blindly: the raw response is parsed
 * defensively, unknown criterion ids are dropped, {@code CRITICAL} is clamped down to
 * {@code MAJOR} (an LLM's opinion alone should never fail an attempt), and the call is bounded
 * by a timeout and a small number of retries so a slow or flaky model never blocks the pipeline
 * forever — see {@link EvaluationPipeline} for what happens when this still fails.
 */
public final class LlmEvaluator implements Evaluator {

    private static final int MAX_FINDINGS = 6;
    private static final int MAX_FIELD_LENGTH = 500;
    private static final int MAX_ALTERNATIVES = 3;

    private final LlmClient client;
    private final PromptBuilder promptBuilder;
    private final ExecutorService executor;
    private final Duration timeout;
    private final int maxAttempts;

    public LlmEvaluator(LlmClient client, ExecutorService executor, Duration timeout, int maxAttempts) {
        this.client = client;
        this.promptBuilder = new PromptBuilder();
        this.executor = executor;
        this.timeout = timeout;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public String name() {
        return "AI design review (" + client.description() + ")";
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public List<Finding> evaluate(EvaluationContext context) {
        String prompt = promptBuilder.build(context);
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String raw = callWithTimeout(prompt);
                return parseAndValidate(raw, context.problem());
            } catch (RuntimeException e) {
                lastError = e;
            }
        }
        throw new LlmEvaluationException(
                "AI review failed after " + maxAttempts + " attempt(s): "
                        + (lastError == null ? "unknown error" : lastError.getMessage()), lastError);
    }

    private String callWithTimeout(String prompt) {
        Future<String> future = executor.submit(() -> client.complete(prompt));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new LlmEvaluationException("AI call timed out after " + timeout, e);
        } catch (ExecutionException e) {
            throw new LlmEvaluationException("AI call failed: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmEvaluationException("Interrupted while waiting for the AI", e);
        }
    }

    private List<Finding> parseAndValidate(String raw, Problem problem) {
        String jsonText = Json.extractFirstObject(raw);
        if (jsonText == null) {
            throw new LlmEvaluationException("AI response did not contain a JSON object: " + truncate(raw, 200));
        }
        Map<String, Object> obj;
        try {
            obj = Json.asObject(Json.parse(jsonText));
        } catch (RuntimeException e) {
            throw new LlmEvaluationException("AI response was not valid JSON: " + e.getMessage(), e);
        }

        List<Finding> findings = new ArrayList<>();
        Object rawFindings = obj.get("findings");
        if (rawFindings instanceof List<?> list) {
            for (Object item : list) {
                if (findings.size() >= MAX_FINDINGS) {
                    break;
                }
                Finding finding = toFinding(item, problem);
                if (finding != null) {
                    findings.add(finding);
                }
            }
        }
        return findings;
    }

    private Finding toFinding(Object item, Problem problem) {
        Map<String, Object> f;
        try {
            f = Json.asObject(item);
        } catch (RuntimeException e) {
            return null;
        }
        String criterionId = asString(f.get("criterionId"));
        if (criterionId == null || !problem.rubric().contains(criterionId)) {
            return null; // unknown criterion ids are dropped, not trusted
        }
        String title = truncate(asString(f.get("title")), MAX_FIELD_LENGTH);
        if (title == null || title.isBlank()) {
            return null;
        }
        Severity severity = parseSeverity(asString(f.get("severity")));
        String evidence = truncate(asString(f.get("evidence")), MAX_FIELD_LENGTH);
        String suggestion = truncate(asString(f.get("suggestion")), MAX_FIELD_LENGTH);
        List<String> alternatives = parseAlternatives(f.get("alternatives"));

        return new Finding(criterionId, severity, title, evidence == null ? "" : evidence,
                suggestion == null ? "" : suggestion, alternatives, FindingSource.LLM);
    }

    private Severity parseSeverity(String raw) {
        if (raw == null) {
            return Severity.MINOR;
        }
        try {
            Severity severity = Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            // The AI's opinion alone must never fail an attempt outright.
            return severity == Severity.CRITICAL ? Severity.MAJOR : severity;
        } catch (IllegalArgumentException e) {
            return Severity.MINOR;
        }
    }

    private List<String> parseAlternatives(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (out.size() >= MAX_ALTERNATIVES) {
                break;
            }
            String s = truncate(asString(item), MAX_FIELD_LENGTH);
            if (s != null && !s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
