package lldpractice.domain;

import java.util.List;

/**
 * An LLD practice problem: enough context for a learner to attempt a design, plus the
 * {@link Rubric} and expected {@link Concept}s used to evaluate what they submit.
 */
public final class Problem {

    private final String id;
    private final String title;
    private final Difficulty difficulty;
    private final String summary;
    private final List<String> requirements;
    private final List<String> thinkingPrompts;
    private final List<String> hints;
    private final Rubric rubric;
    private final List<Concept> concepts;

    public Problem(String id, String title, Difficulty difficulty, String summary,
                    List<String> requirements, List<String> thinkingPrompts, List<String> hints,
                    Rubric rubric, List<Concept> concepts) {
        this.id = id;
        this.title = title;
        this.difficulty = difficulty;
        this.summary = summary;
        this.requirements = List.copyOf(requirements);
        this.thinkingPrompts = List.copyOf(thinkingPrompts);
        this.hints = List.copyOf(hints);
        this.rubric = rubric;
        this.concepts = List.copyOf(concepts);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public String summary() {
        return summary;
    }

    public List<String> requirements() {
        return requirements;
    }

    public List<String> thinkingPrompts() {
        return thinkingPrompts;
    }

    public List<String> hints() {
        return hints;
    }

    public Rubric rubric() {
        return rubric;
    }

    public List<Concept> concepts() {
        return concepts;
    }
}
