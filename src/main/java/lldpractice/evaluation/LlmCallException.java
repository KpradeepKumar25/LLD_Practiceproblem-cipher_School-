package lldpractice.evaluation;

/** Thrown by an {@link LlmClient} when the call fails (network error, non-200 response, bad shape). */
public class LlmCallException extends RuntimeException {
    public LlmCallException(String message) {
        super(message);
    }

    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
