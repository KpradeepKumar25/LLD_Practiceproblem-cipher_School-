package lldpractice.domain.exceptions;

import lldpractice.domain.AttemptStatus;

/** Thrown when an {@link lldpractice.domain.Attempt} tries to move to a status its current state forbids. */
public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(AttemptStatus from, AttemptStatus to) {
        super("Cannot transition attempt from " + from + " to " + to);
    }
}
