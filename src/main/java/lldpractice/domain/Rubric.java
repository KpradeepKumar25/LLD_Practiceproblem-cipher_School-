package lldpractice.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An ordered set of {@link RubricCriterion}s used to score every attempt at a {@link Problem}.
 * The same six criteria are reused across problems so feedback stays comparable across attempts
 * and across problems: requirement coverage, abstraction quality, single responsibility,
 * extensibility, encapsulation, and reasoning/trade-offs.
 */
public final class Rubric {

    private final List<RubricCriterion> criteria;
    private final Map<String, RubricCriterion> byId;

    public Rubric(List<RubricCriterion> criteria) {
        this.criteria = List.copyOf(criteria);
        Map<String, RubricCriterion> index = new LinkedHashMap<>();
        for (RubricCriterion c : criteria) {
            index.put(c.id(), c);
        }
        this.byId = index;

        double total = criteria.stream().mapToDouble(RubricCriterion::weight).sum();
        if (Math.abs(total - 1.0) > 0.01) {
            throw new IllegalArgumentException("Rubric weights must sum to 1.0, got " + total);
        }
    }

    public List<RubricCriterion> criteria() {
        return criteria;
    }

    public Optional<RubricCriterion> find(String criterionId) {
        return Optional.ofNullable(byId.get(criterionId));
    }

    public boolean contains(String criterionId) {
        return byId.containsKey(criterionId);
    }

    /** The standard six-criterion rubric shared by every problem in the catalog. */
    public static Rubric standard() {
        return new Rubric(List.of(
                new RubricCriterion("requirement_coverage", "Requirement coverage",
                        "Does the design address every stated requirement?", 0.25),
                new RubricCriterion("abstraction", "Abstractions",
                        "Are the right entities, interfaces and extension points identified?", 0.20),
                new RubricCriterion("responsibility", "Single responsibility",
                        "Is behaviour split across classes with one clear job each?", 0.20),
                new RubricCriterion("extensibility", "Extensibility",
                        "Can a new variant be added without rewriting existing classes?", 0.15),
                new RubricCriterion("encapsulation", "Encapsulation",
                        "Is internal state protected behind behaviour, not exposed as public fields?", 0.10),
                new RubricCriterion("reasoning", "Reasoning & trade-offs",
                        "Are assumptions and trade-offs explained, not just asserted?", 0.10)
        ));
    }
}
