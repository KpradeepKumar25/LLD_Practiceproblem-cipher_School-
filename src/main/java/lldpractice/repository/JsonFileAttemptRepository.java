package lldpractice.repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lldpractice.domain.Attempt;
import lldpractice.domain.AttemptStatus;
import lldpractice.domain.Feedback;
import lldpractice.domain.Finding;
import lldpractice.domain.FindingSource;
import lldpractice.domain.Severity;
import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;
import lldpractice.util.Json;

/**
 * A second {@link AttemptRepository} implementation, kept to prove the Repository seam actually
 * buys swappability: {@link lldpractice.service.PracticeService} depends only on the interface,
 * so wiring this in instead of {@link InMemoryAttemptRepository} (see {@code App}) requires no
 * other code change. Writes one JSON file per attempt; reads rebuild the full object graph,
 * including {@link Attempt}'s otherwise-private lifecycle state, via {@link Attempt#restore}.
 */
public final class JsonFileAttemptRepository implements AttemptRepository {

    private final Path directory;
    private final InMemoryAttemptRepository cache = new InMemoryAttemptRepository();

    public JsonFileAttemptRepository(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        loadExisting();
    }

    private void loadExisting() {
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> jsonFiles = files.filter(p -> p.toString().endsWith(".json")).toList();
            for (Path file : jsonFiles) {
                cache.save(deserialize(Files.readString(file)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Attempt save(Attempt attempt) {
        cache.save(attempt);
        try {
            Files.writeString(directory.resolve(attempt.id() + ".json"), Json.write(serialize(attempt)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return attempt;
    }

    @Override
    public Optional<Attempt> findById(String id) {
        return cache.findById(id);
    }

    @Override
    public List<Attempt> findByLearnerAndProblem(String learnerName, String problemId) {
        return cache.findByLearnerAndProblem(learnerName, problemId);
    }

    @Override
    public List<Attempt> findByLearner(String learnerName) {
        return cache.findByLearner(learnerName);
    }

    @Override
    public int highestAttemptNumber(String learnerName, String problemId) {
        return cache.highestAttemptNumber(learnerName, problemId);
    }

    // ---- serialization -------------------------------------------------

    private Map<String, Object> serialize(Attempt a) {
        Map<String, Object> map = Json.object(
                "id", a.id(),
                "learnerName", a.learnerName(),
                "problemId", a.problemId(),
                "attemptNumber", a.attemptNumber(),
                "createdAt", a.createdAt().toString(),
                "status", a.status().name(),
                "submission", serializeSubmission(a.submission()),
                "failureReason", a.failureReason(),
                "submittedAt", a.submittedAt() == null ? null : a.submittedAt().toString(),
                "completedAt", a.completedAt() == null ? null : a.completedAt().toString()
        );
        map.put("feedback", a.feedback() == null ? null : serializeFeedback(a.feedback()));
        return map;
    }

    private Map<String, Object> serializeSubmission(Submission s) {
        return Json.object(
                "assumptions", s.assumptions(),
                "classOutline", s.classOutline(),
                "code", s.code(),
                "tradeOffs", s.tradeOffs(),
                "format", s.format().name()
        );
    }

    private Map<String, Object> serializeFeedback(Feedback f) {
        List<Map<String, Object>> findings = f.findings().stream().map(this::serializeFinding).toList();
        return Json.object(
                "findings", findings,
                "criterionScores", f.criterionScores(),
                "overallScore", f.overallScore(),
                "summary", f.summary(),
                "degraded", f.degraded(),
                "notes", f.notes()
        );
    }

    private Map<String, Object> serializeFinding(Finding finding) {
        return Json.object(
                "criterionId", finding.criterionId(),
                "severity", finding.severity().name(),
                "title", finding.title(),
                "evidence", finding.evidence(),
                "suggestion", finding.suggestion(),
                "alternatives", finding.alternatives(),
                "source", finding.source().name()
        );
    }

    // ---- deserialization -------------------------------------------------

    @SuppressWarnings("unchecked")
    private Attempt deserialize(String json) {
        Map<String, Object> map = Json.asObject(Json.parse(json));
        Submission submission = deserializeSubmission((Map<String, Object>) map.get("submission"));
        Object feedbackRaw = map.get("feedback");
        Feedback feedback = feedbackRaw == null ? null : deserializeFeedback((Map<String, Object>) feedbackRaw);

        return Attempt.restore(
                (String) map.get("id"),
                (String) map.get("learnerName"),
                (String) map.get("problemId"),
                ((Number) map.get("attemptNumber")).intValue(),
                Instant.parse((String) map.get("createdAt")),
                submission,
                AttemptStatus.valueOf((String) map.get("status")),
                feedback,
                (String) map.get("failureReason"),
                parseInstant((String) map.get("submittedAt")),
                parseInstant((String) map.get("completedAt"))
        );
    }

    private Instant parseInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private Submission deserializeSubmission(Map<String, Object> map) {
        return new Submission(
                (String) map.get("assumptions"),
                (String) map.get("classOutline"),
                (String) map.get("code"),
                (String) map.get("tradeOffs"),
                SubmissionFormat.valueOf((String) map.get("format"))
        );
    }

    @SuppressWarnings("unchecked")
    private Feedback deserializeFeedback(Map<String, Object> map) {
        List<Finding> findings = ((List<Object>) map.get("findings")).stream()
                .map(o -> deserializeFinding((Map<String, Object>) o))
                .collect(Collectors.toCollection(ArrayList::new));
        Map<String, Object> rawScores = (Map<String, Object>) map.get("criterionScores");
        Map<String, Integer> scores = rawScores.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> ((Number) e.getValue()).intValue(),
                        (a, b) -> a, java.util.LinkedHashMap::new));
        List<String> notes = ((List<Object>) map.get("notes")).stream().map(Object::toString).toList();

        return new Feedback(findings, scores, ((Number) map.get("overallScore")).intValue(),
                (String) map.get("summary"), (Boolean) map.get("degraded"), notes);
    }

    @SuppressWarnings("unchecked")
    private Finding deserializeFinding(Map<String, Object> map) {
        List<String> alternatives = ((List<Object>) map.get("alternatives")).stream().map(Object::toString).toList();
        return new Finding(
                (String) map.get("criterionId"),
                Severity.valueOf((String) map.get("severity")),
                (String) map.get("title"),
                (String) map.get("evidence"),
                (String) map.get("suggestion"),
                alternatives,
                FindingSource.valueOf((String) map.get("source"))
        );
    }
}
