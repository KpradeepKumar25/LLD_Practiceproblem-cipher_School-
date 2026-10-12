package lldpractice.domain.exceptions;

/** Thrown when learner input fails validation (empty, too large, unknown format, etc). */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
