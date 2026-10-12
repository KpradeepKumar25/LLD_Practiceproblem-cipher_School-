package lldpractice.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lldpractice.domain.Problem;

/** The only {@link ProblemRepository} the platform ships with: the problem catalog is fixed
 * content loaded once at startup, so a simple in-memory map is enough. */
public final class InMemoryProblemRepository implements ProblemRepository {

    private final Map<String, Problem> problems = new LinkedHashMap<>();

    public InMemoryProblemRepository(List<Problem> seed) {
        for (Problem p : seed) {
            problems.put(p.id(), p);
        }
    }

    @Override
    public List<Problem> findAll() {
        return List.copyOf(problems.values());
    }

    @Override
    public Optional<Problem> findById(String id) {
        return Optional.ofNullable(problems.get(id));
    }
}
