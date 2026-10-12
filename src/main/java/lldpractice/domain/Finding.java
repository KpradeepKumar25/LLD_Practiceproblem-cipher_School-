package lldpractice.domain;

import java.util.List;

/**
 * One piece of feedback against a single {@link RubricCriterion}. Every finding must point to
 * concrete evidence in the learner's own submission (never a generic template message) and,
 * where relevant, name an alternative approach that would also be valid — the thing that makes
 * feedback trustworthy when more than one design can be correct.
 */
public final class Finding {

    private final String criterionId;
    private final Severity severity;
    private final String title;
    private final String evidence;
    private final String suggestion;
    private final List<String> alternatives;
    private final FindingSource source;

    public Finding(String criterionId, Severity severity, String title, String evidence,
                    String suggestion, List<String> alternatives, FindingSource source) {
        this.criterionId = criterionId;
        this.severity = severity;
        this.title = title;
        this.evidence = evidence;
        this.suggestion = suggestion;
        this.alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        this.source = source;
    }

    public String criterionId() {
        return criterionId;
    }

    public Severity severity() {
        return severity;
    }

    public String title() {
        return title;
    }

    public String evidence() {
        return evidence;
    }

    public String suggestion() {
        return suggestion;
    }

    public List<String> alternatives() {
        return alternatives;
    }

    public FindingSource source() {
        return source;
    }
}
