package lldpractice.domain;

/**
 * How serious a {@link Finding} is. Ordered from most to least severe so findings can be
 * sorted for display. {@link #POSITIVE} notes something done well and carries no penalty.
 */
public enum Severity {
    CRITICAL, MAJOR, MINOR, INFO, POSITIVE;

    /** Score penalty applied to the owning criterion for one finding of this severity. */
    public int penalty() {
        return switch (this) {
            case CRITICAL -> 35;
            case MAJOR -> 20;
            case MINOR -> 8;
            case INFO, POSITIVE -> 0;
        };
    }
}
