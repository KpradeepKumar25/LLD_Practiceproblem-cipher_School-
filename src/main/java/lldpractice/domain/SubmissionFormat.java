package lldpractice.domain;

import java.util.Locale;

import lldpractice.domain.exceptions.ValidationException;

/**
 * The language/notation the {@code code} section of a {@link Submission} is written in.
 * New formats register their own {@link lldpractice.design.DesignExtractor} without
 * requiring changes to evaluators or services.
 */
public enum SubmissionFormat {
    JAVA;

    /** Parses a format name from user input (e.g. a web form field), rejecting anything unknown. */
    public static SubmissionFormat parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Submission format is required.");
        }
        try {
            return SubmissionFormat.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown submission format: " + raw);
        }
    }
}
