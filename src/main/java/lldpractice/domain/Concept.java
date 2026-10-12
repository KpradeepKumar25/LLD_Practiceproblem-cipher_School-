package lldpractice.domain;

import java.util.List;
import java.util.Locale;

/**
 * A design idea a strong solution is expected to contain (an entity, behaviour or
 * extension point), identified by any one of several acceptable names.
 *
 * <p>LLD problems have more than one valid vocabulary: a Parking Lot's "slot" might be
 * named {@code Spot}, {@code Slot} or {@code Bay} by different learners. Matching against
 * a fixed model answer would unfairly penalise equally good designs that chose different
 * names, so every expected concept carries the full list of names that count as a match.
 */
public final class Concept {

    /** What the concept represents in the design, e.g. "a parking slot abstraction". */
    private final String label;

    /** Names (case-insensitive, matched as substrings of identifiers) that satisfy this concept. */
    private final List<String> acceptedNames;

    /** Whether omitting this concept entirely should be treated as a critical gap. */
    private final boolean required;

    public Concept(String label, List<String> acceptedNames, boolean required) {
        this.label = label;
        this.acceptedNames = List.copyOf(acceptedNames);
        this.required = required;
    }

    public String label() {
        return label;
    }

    public List<String> acceptedNames() {
        return acceptedNames;
    }

    public boolean required() {
        return required;
    }

    /** Returns true if any accepted name appears (case-insensitively) inside the given identifier. */
    public boolean matches(String identifier) {
        if (identifier == null) {
            return false;
        }
        String lower = identifier.toLowerCase(Locale.ROOT);
        for (String name : acceptedNames) {
            if (lower.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
