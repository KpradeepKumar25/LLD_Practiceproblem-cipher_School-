package lldpractice.domain;

import java.util.Map;
import java.util.Set;

/**
 * The lifecycle state of an {@link Attempt}.
 *
 * <pre>
 *            submit                 evaluation starts
 *   DRAFT ----------&gt; SUBMITTED --------------------&gt; EVALUATING
 *                                                       |   |   \
 *                                           all criteria|   |    \essential evaluator throws
 *                                             pass fully |   |     \or deterministic parse fails
 *                                                        v   |      v
 *                                                  COMPLETED |    FAILED
 *                                                            v      |
 *                                               PARTIALLY_COMPLETED |
 *                                                            ^      |
 *                                                            |      |
 *                                                 retry ------+------+
 * </pre>
 *
 * Only {@link #FAILED} and {@link #PARTIALLY_COMPLETED} may retry back into {@link #EVALUATING}.
 */
public enum AttemptStatus {
    DRAFT,
    SUBMITTED,
    EVALUATING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED;

    private static final Map<AttemptStatus, Set<AttemptStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(SUBMITTED),
            SUBMITTED, Set.of(EVALUATING),
            EVALUATING, Set.of(COMPLETED, PARTIALLY_COMPLETED, FAILED),
            COMPLETED, Set.of(),
            PARTIALLY_COMPLETED, Set.of(EVALUATING),
            FAILED, Set.of(EVALUATING)
    );

    public boolean canTransitionTo(AttemptStatus next) {
        return ALLOWED.get(this).contains(next);
    }
}
