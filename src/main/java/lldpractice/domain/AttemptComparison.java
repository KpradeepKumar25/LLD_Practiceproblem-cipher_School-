package lldpractice.domain;

import java.util.List;
import java.util.Map;

/**
 * The difference between two attempts at the same problem, used by the "Compare with previous"
 * view so a learner can see whether they actually improved.
 *
 * <p>Only deterministic findings are compared: they are reproducible, so "resolved" genuinely
 * means the underlying structural issue is gone, not that the LLM happened to phrase things
 * differently on a re-run. Findings are matched by (criterionId, title), which identifies the
 * specific rule that fired.
 */
public final class AttemptComparison {

    private final String previousAttemptId;
    private final String currentAttemptId;
    private final List<Finding> resolved;
    private final List<Finding> remaining;
    private final List<Finding> introduced;
    private final int previousScore;
    private final int currentScore;
    private final Map<String, Integer> criterionScoreDeltas;

    public AttemptComparison(String previousAttemptId, String currentAttemptId,
                              List<Finding> resolved, List<Finding> remaining, List<Finding> introduced,
                              int previousScore, int currentScore, Map<String, Integer> criterionScoreDeltas) {
        this.previousAttemptId = previousAttemptId;
        this.currentAttemptId = currentAttemptId;
        this.resolved = List.copyOf(resolved);
        this.remaining = List.copyOf(remaining);
        this.introduced = List.copyOf(introduced);
        this.previousScore = previousScore;
        this.currentScore = currentScore;
        this.criterionScoreDeltas = Map.copyOf(criterionScoreDeltas);
    }

    public String previousAttemptId() {
        return previousAttemptId;
    }

    public String currentAttemptId() {
        return currentAttemptId;
    }

    public List<Finding> resolved() {
        return resolved;
    }

    public List<Finding> remaining() {
        return remaining;
    }

    public List<Finding> introduced() {
        return introduced;
    }

    public int previousScore() {
        return previousScore;
    }

    public int currentScore() {
        return currentScore;
    }

    public int scoreDelta() {
        return currentScore - previousScore;
    }

    public Map<String, Integer> criterionScoreDeltas() {
        return criterionScoreDeltas;
    }
}
