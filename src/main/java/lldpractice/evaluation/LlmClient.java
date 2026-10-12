package lldpractice.evaluation;

/**
 * A minimal abstraction over "send a prompt, get text back" so {@link LlmEvaluator} never
 * depends on a specific provider. {@link AnthropicLlmClient} calls the real API when a key is
 * configured; {@link MockLlmClient} is the offline fallback so the whole platform works with
 * zero setup.
 */
public interface LlmClient {

    /** Sends {@code prompt} and returns the raw text response. May throw on network/API failure. */
    String complete(String prompt);

    /** A short label shown in degraded-mode notices, e.g. "Anthropic (claude-sonnet-5-5)" or "Mock reviewer". */
    String description();

    /** True for a client that never leaves the machine (e.g. {@link MockLlmClient}), used to add
     * a "this is heuristic, not a full AI review" note to the feedback. */
    boolean offline();
}
