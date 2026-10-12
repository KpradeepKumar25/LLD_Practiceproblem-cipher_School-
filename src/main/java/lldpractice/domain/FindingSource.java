package lldpractice.domain;

/**
 * Which evaluator produced a {@link Finding}. Shown in the UI so learners understand which
 * feedback is a deterministic rule (always reproducible) and which is an LLM's judgement
 * (advisory, and only one of several reasonable readings).
 */
public enum FindingSource {
    DETERMINISTIC, LLM
}
