package lldpractice.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

import lldpractice.domain.Attempt;
import lldpractice.domain.Feedback;
import lldpractice.domain.Finding;
import lldpractice.domain.Problem;
import lldpractice.domain.exceptions.ValidationException;
import lldpractice.service.PracticeService;
import lldpractice.util.Json;

/** The small read-only JSON API: problems, one attempt, and a learner's history. Kept alongside
 * the HTML pages rather than as a separate service, since this MVP has one process and one
 * set of domain objects to expose either way. */
public final class ApiHandlers {

    private final PracticeService practiceService;

    public ApiHandlers(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    public void problems(HttpExchange exchange, Map<String, String> params) throws IOException {
        List<Map<String, Object>> json = practiceService.listProblems().stream().map(this::problemJson).toList();
        HttpUtil.sendJson(exchange, 200, Json.write(json));
    }

    public void attempt(HttpExchange exchange, Map<String, String> params) throws IOException {
        Attempt attempt = practiceService.getAttempt(params.get("id"));
        HttpUtil.sendJson(exchange, 200, Json.write(attemptJson(attempt)));
    }

    public void history(HttpExchange exchange, Map<String, String> params) throws IOException {
        Map<String, String> query = HttpUtil.queryParams(exchange);
        String learner = query.get("learner");
        if (learner == null || learner.isBlank()) {
            throw new ValidationException("Query parameter 'learner' is required.");
        }
        List<Map<String, Object>> json = practiceService.history(learner).stream().map(this::attemptJson).toList();
        HttpUtil.sendJson(exchange, 200, Json.write(json));
    }

    private Map<String, Object> problemJson(Problem p) {
        return Json.object(
                "id", p.id(),
                "title", p.title(),
                "difficulty", p.difficulty().name(),
                "summary", p.summary(),
                "requirements", p.requirements()
        );
    }

    private Map<String, Object> attemptJson(Attempt a) {
        Map<String, Object> map = Json.object(
                "id", a.id(),
                "learnerName", a.learnerName(),
                "problemId", a.problemId(),
                "attemptNumber", a.attemptNumber(),
                "status", a.status().name(),
                "createdAt", a.createdAt().toString(),
                "failureReason", a.failureReason()
        );
        map.put("feedback", a.feedback() == null ? null : feedbackJson(a.feedback()));
        return map;
    }

    private Map<String, Object> feedbackJson(Feedback f) {
        return Json.object(
                "overallScore", f.overallScore(),
                "criterionScores", f.criterionScores(),
                "summary", f.summary(),
                "degraded", f.degraded(),
                "notes", f.notes(),
                "findings", f.findings().stream().map(this::findingJson).toList()
        );
    }

    private Map<String, Object> findingJson(Finding f) {
        return Json.object(
                "criterionId", f.criterionId(),
                "severity", f.severity().name(),
                "title", f.title(),
                "evidence", f.evidence(),
                "suggestion", f.suggestion(),
                "alternatives", f.alternatives(),
                "source", f.source().name()
        );
    }
}
