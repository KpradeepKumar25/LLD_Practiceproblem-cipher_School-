package lldpractice.domain.exceptions;

/** Thrown when a requested entity (problem, attempt, learner history) does not exist. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
