package lldpractice.repository;

import java.util.List;
import java.util.Optional;

import lldpractice.domain.Attempt;

/**
 * Storage seam for {@link Attempt}s. {@link InMemoryAttemptRepository} is what the app runs
 * with; {@link JsonFileAttemptRepository} is a second implementation kept purely to demonstrate
 * that {@link lldpractice.service.PracticeService} never needs to change to get durable storage
 * instead of in-memory.
 */
public interface AttemptRepository {

    Attempt save(Attempt attempt);

    Optional<Attempt> findById(String id);

    /** All attempts by one learner for one problem, ordered by attempt number ascending. */
    List<Attempt> findByLearnerAndProblem(String learnerName, String problemId);

    /** All attempts by one learner across every problem, most recent first. */
    List<Attempt> findByLearner(String learnerName);

    /** The highest attempt number this learner has used for this problem, or 0 if none. */
    int highestAttemptNumber(String learnerName, String problemId);
}
