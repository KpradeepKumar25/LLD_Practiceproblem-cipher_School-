package lldpractice.design;

import lldpractice.domain.Submission;

/**
 * Turns a {@link Submission}'s code into a format-neutral {@link DesignModel}.
 *
 * <p>This is the seam that lets the platform accept another submission format later (a
 * diagram, a different language) without touching any {@link lldpractice.evaluation.Evaluator}:
 * register a new implementation in {@link ExtractorRegistry} and every evaluator keeps working
 * unchanged because it only ever reads a {@link DesignModel}.
 */
public interface DesignExtractor {

    /** The submission format this extractor understands. */
    lldpractice.domain.SubmissionFormat format();

    /** Parses {@code submission.code()} into a {@link DesignModel}. Never throws on malformed input;
     * parse problems are reported as {@link DesignModel#warnings()} instead. */
    DesignModel extract(Submission submission);
}
