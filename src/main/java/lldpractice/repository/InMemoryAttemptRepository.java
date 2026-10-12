package lldpractice.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import lldpractice.domain.Attempt;

/** Default {@link AttemptRepository}: a thread-safe in-memory map, since attempts don't need
 * to survive a restart for this MVP. See {@link JsonFileAttemptRepository} for a persistent
 * alternative behind the same interface. */
public final class InMemoryAttemptRepository implements AttemptRepository {

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    @Override
    public Attempt save(Attempt attempt) {
        attempts.put(attempt.id(), attempt);
        return attempt;
    }

    @Override
    public Optional<Attempt> findById(String id) {
        return Optional.ofNullable(attempts.get(id));
    }

    @Override
    public List<Attempt> findByLearnerAndProblem(String learnerName, String problemId) {
        return attempts.values().stream()
                .filter(a -> a.learnerName().equalsIgnoreCase(learnerName) && a.problemId().equals(problemId))
                .sorted(Comparator.comparingInt(Attempt::attemptNumber))
                .toList();
    }

    @Override
    public List<Attempt> findByLearner(String learnerName) {
        return attempts.values().stream()
                .filter(a -> a.learnerName().equalsIgnoreCase(learnerName))
                .sorted(Comparator.comparing(Attempt::createdAt).reversed())
                .toList();
    }

    @Override
    public int highestAttemptNumber(String learnerName, String problemId) {
        return attempts.values().stream()
                .filter(a -> a.learnerName().equalsIgnoreCase(learnerName) && a.problemId().equals(problemId))
                .mapToInt(Attempt::attemptNumber)
                .max()
                .orElse(0);
    }
}
