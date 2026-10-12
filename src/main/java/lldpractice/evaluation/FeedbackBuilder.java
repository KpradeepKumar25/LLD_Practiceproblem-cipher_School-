package lldpractice.evaluation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lldpractice.domain.Feedback;
import lldpractice.domain.Finding;
import lldpractice.domain.Problem;
import lldpractice.domain.RubricCriterion;
import lldpractice.domain.Severity;
import lldpractice.evaluation.EvaluationPipeline.PipelineResult;

/**
 * Turns a {@link PipelineResult} into the {@link Feedback} shown to the learner: a 0-100 score
 * per criterion (100 minus each finding's severity penalty, floored at 0), the rubric-weighted
 * overall score, findings ordered most-severe first, and a short human summary.
 */
public final class FeedbackBuilder {

    public Feedback build(Problem problem, PipelineResult result) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (RubricCriterion criterion : problem.rubric().criteria()) {
            int score = 100;
            for (Finding finding : result.findings()) {
                if (finding.criterionId().equals(criterion.id())) {
                    score -= finding.severity().penalty();
                }
            }
            scores.put(criterion.id(), Math.max(0, score));
        }

        double weighted = 0;
        for (RubricCriterion criterion : problem.rubric().criteria()) {
            weighted += scores.get(criterion.id()) * criterion.weight();
        }
        int overall = (int) Math.round(Math.max(0, Math.min(100, weighted)));

        List<Finding> ordered = result.findings().stream()
                .sorted(Comparator.comparing(Finding::severity, Comparator.comparingInt(Enum::ordinal)))
                .toList();

        String summary = buildSummary(overall, result.findings());

        return new Feedback(ordered, scores, overall, summary, result.degraded(), result.notes());
    }

    private String buildSummary(int overall, List<Finding> findings) {
        long critical = findings.stream().filter(f -> f.severity() == Severity.CRITICAL).count();
        long major = findings.stream().filter(f -> f.severity() == Severity.MAJOR).count();
        long positive = findings.stream().filter(f -> f.severity() == Severity.POSITIVE).count();

        String headline;
        if (overall >= 85) {
            headline = "Strong design overall.";
        } else if (overall >= 70) {
            headline = "Solid design with a few gaps.";
        } else if (overall >= 50) {
            headline = "Workable design with notable gaps.";
        } else {
            headline = "Needs significant rework before the structure holds up.";
        }

        StringBuilder sb = new StringBuilder(headline).append(' ');
        if (critical > 0) {
            sb.append(critical).append(critical == 1 ? " critical issue" : " critical issues").append(", ");
        }
        if (major > 0) {
            sb.append(major).append(major == 1 ? " major issue" : " major issues").append(", ");
        }
        if (positive > 0) {
            sb.append("and ").append(positive).append(positive == 1 ? " thing done well" : " things done well");
        }
        String tail = sb.toString().trim();
        if (tail.endsWith(",")) {
            tail = tail.substring(0, tail.length() - 1);
        }
        return tail + ".";
    }
}
