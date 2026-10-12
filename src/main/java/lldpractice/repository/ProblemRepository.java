package lldpractice.repository;

import java.util.List;
import java.util.Optional;

import lldpractice.domain.Problem;

/** Storage seam for {@link Problem}s. Only an in-memory implementation ships today, but the
 * interface is what lets a database-backed one replace it later without touching callers. */
public interface ProblemRepository {

    List<Problem> findAll();

    Optional<Problem> findById(String id);
}
