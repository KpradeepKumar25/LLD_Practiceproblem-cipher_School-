package lldpractice.domain;

/**
 * One scored dimension of design quality, e.g. "Requirement coverage" or "Encapsulation".
 * Weights across a {@link Rubric} are expected to sum to 1.0.
 */
public final class RubricCriterion {

    private final String id;
    private final String label;
    private final String description;
    private final double weight;

    public RubricCriterion(String id, String label, String description, double weight) {
        if (weight < 0 || weight > 1) {
            throw new IllegalArgumentException("weight must be between 0 and 1: " + weight);
        }
        this.id = id;
        this.label = label;
        this.description = description;
        this.weight = weight;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public double weight() {
        return weight;
    }
}
