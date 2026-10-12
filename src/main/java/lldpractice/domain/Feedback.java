package lldpractice.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of evaluating one {@link Attempt}: findings grouped implicitly by criterion,
 * a 0-100 score per criterion, a weighted overall score, and a human-readable summary.
 *
 * <p>{@code degraded} is set when a non-essential evaluator (currently the LLM) failed or
 * timed out, so the feedback is deterministic-only even though the attempt still completed.
 */
public final class Feedback {

    private final List<Finding> findings;
    private final Map<String, Integer> criterionScores;
    private final int overallScore;
    private final String summary;
    private final boolean degraded;
    private final List<String> notes;

    public Feedback(List<Finding> findings, Map<String, Integer> criterionScores, int overallScore,
                     String summary, boolean degraded, List<String> notes) {
        this.findings = List.copyOf(findings);
        this.criterionScores = new LinkedHashMap<>(criterionScores);
        this.overallScore = overallScore;
        this.summary = summary;
        this.degraded = degraded;
        this.notes = List.copyOf(notes);
    }

    public List<Finding> findings() {
        return findings;
    }

    public Map<String, Integer> criterionScores() {
        return criterionScores;
    }

    public int overallScore() {
        return overallScore;
    }

    public String summary() {
        return summary;
    }

    public boolean degraded() {
        return degraded;
    }

    public List<String> notes() {
        return notes;
    }

    public List<Finding> findingsFor(String criterionId) {
        return findings.stream().filter(f -> f.criterionId().equals(criterionId)).toList();
    }
}
