package lldpractice.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sun.net.httpserver.HttpExchange;

import lldpractice.domain.Attempt;
import lldpractice.domain.AttemptComparison;
import lldpractice.domain.AttemptStatus;
import lldpractice.domain.Feedback;
import lldpractice.domain.Finding;
import lldpractice.domain.Problem;
import lldpractice.domain.RubricCriterion;
import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;
import lldpractice.service.PracticeService;

/** Handlers for the whole attempt lifecycle: edit draft, submit, watch evaluation, read
 * feedback, retry, try again, and compare with a previous attempt. */
public final class AttemptHandlers {

    private final PracticeService practiceService;

    public AttemptHandlers(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    public void view(HttpExchange exchange, Map<String, String> params) throws IOException {
        String learner = Learner.of(exchange);
        Attempt attempt = practiceService.getAttempt(params.get("id"));
        Problem problem = practiceService.getProblem(attempt.problemId());

        String body = switch (attempt.status()) {
            case DRAFT -> renderForm(problem, attempt, null);
            case SUBMITTED, EVALUATING -> renderWaiting(problem, attempt);
            case COMPLETED, PARTIALLY_COMPLETED -> renderResult(problem, attempt, learner);
            case FAILED -> renderFailed(problem, attempt);
        };
        boolean autoRefresh = attempt.status() == AttemptStatus.SUBMITTED || attempt.status() == AttemptStatus.EVALUATING;
        String html = autoRefresh ? withAutoRefresh(Html.shell(problem.title(), learner, body)) : Html.shell(problem.title(), learner, body);
        HttpUtil.sendHtml(exchange, 200, html);
    }

    private String withAutoRefresh(String html) {
        return html.replace("<head>", "<head><meta http-equiv='refresh' content='2'>");
    }

    private String renderForm(Problem problem, Attempt attempt, String errorMessage) {
        Submission s = attempt.submission();
        StringBuilder body = new StringBuilder();
        body.append("<h1>").append(Html.escape(problem.title())).append(" &mdash; Attempt #")
                .append(attempt.attemptNumber()).append("</h1>")
                .append(Html.statusBadge(attempt.status()));
        if (errorMessage != null) {
            body.append("<div class='notice'>").append(Html.escape(errorMessage)).append("</div>");
        }
        body.append("<form method='post' action='/attempts/").append(attempt.id()).append("/draft'>");

        body.append("<label>Assumptions <span class='hint'>what you're deliberately simplifying or taking for granted</span></label>")
                .append("<textarea name='assumptions' rows='3'>").append(Html.escape(s.assumptions())).append("</textarea>");

        body.append("<label>Class outline <span class='hint'>the classes/interfaces you plan and what each is responsible for</span></label>")
                .append("<textarea name='classOutline' rows='5'>").append(Html.escape(s.classOutline())).append("</textarea>");

        body.append("<label>Code <span class='hint'>Java</span></label>")
                .append("<textarea name='code' rows='16'>").append(Html.escape(s.code())).append("</textarea>");

        body.append("<label>Trade-offs <span class='hint'>what you chose not to do, and why</span></label>")
                .append("<textarea name='tradeOffs' rows='3'>").append(Html.escape(s.tradeOffs())).append("</textarea>");

        body.append("<div class='row' style='margin-top:16px'>")
                .append("<button class='btn secondary' type='submit'>Save draft</button>")
                .append("<button class='btn' type='submit' formaction='/attempts/").append(attempt.id()).append("/submit'>Submit for feedback</button>")
                .append("</div></form>");
        return body.toString();
    }

    private String renderWaiting(Problem problem, Attempt attempt) {
        return "<h1>" + Html.escape(problem.title()) + " &mdash; Attempt #" + attempt.attemptNumber() + "</h1>"
                + Html.statusBadge(attempt.status())
                + "<div class='card'><p>Evaluation is running. This page refreshes automatically every couple of seconds.</p></div>";
    }

    private String renderFailed(Problem problem, Attempt attempt) {
        return "<h1>" + Html.escape(problem.title()) + " &mdash; Attempt #" + attempt.attemptNumber() + "</h1>"
                + Html.statusBadge(attempt.status())
                + "<div class='card'><p><b>Evaluation failed:</b> " + Html.escape(attempt.failureReason()) + "</p>"
                + "<form method='post' action='/attempts/" + attempt.id() + "/retry'>"
                + "<button class='btn' type='submit'>Retry evaluation</button></form></div>";
    }

    private String renderResult(Problem problem, Attempt attempt, String learner) {
        Feedback fb = attempt.feedback();
        StringBuilder body = new StringBuilder();
        body.append("<h1>").append(Html.escape(problem.title())).append(" &mdash; Attempt #")
                .append(attempt.attemptNumber()).append("</h1>")
                .append(Html.statusBadge(attempt.status()));

        if (fb.degraded()) {
            body.append("<div class='notice'>AI review did not complete, so this feedback is rule-based only. ");
            for (String note : fb.notes()) {
                body.append(Html.escape(note)).append(' ');
            }
            body.append("<form method='post' action='/attempts/").append(attempt.id())
                    .append("/retry' style='display:inline'><button class='btn secondary' type='submit' style='margin-left:8px'>Retry AI review</button></form>")
                    .append("</div>");
        }

        body.append("<div class='card'><div class='overall'>").append(fb.overallScore()).append("/100</div>")
                .append("<p>").append(Html.escape(fb.summary())).append("</p>");
        for (RubricCriterion c : problem.rubric().criteria()) {
            body.append(Html.scoreBar(c.label(), fb.criterionScores().getOrDefault(c.id(), 0)));
        }
        body.append("</div>");

        for (RubricCriterion c : problem.rubric().criteria()) {
            List<Finding> findings = fb.findingsFor(c.id());
            if (findings.isEmpty()) {
                continue;
            }
            body.append("<h2>").append(Html.escape(c.label())).append("</h2>");
            for (Finding f : findings) {
                body.append(renderFinding(f));
            }
        }

        body.append("<div class='row' style='margin-top:20px'>")
                .append("<a class='btn' href='/attempts/").append(attempt.id()).append("/try-again'>Try again</a>");

        Optional<Attempt> previous = findPrevious(learner, attempt);
        if (previous.isPresent() && previous.get().feedback() != null) {
            body.append("<a class='btn secondary' href='/attempts/").append(attempt.id())
                    .append("/compare?previous=").append(previous.get().id()).append("'>Compare with previous</a>");
        }
        body.append("<a class='btn secondary' href='/problems/").append(problem.id()).append("'>Back to problem</a>")
                .append("</div>");
        return body.toString();
    }

    private String renderFinding(Finding f) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='finding ").append(f.severity().name()).append("'>")
                .append("<div class='title'>").append(Html.severityBadge(f.severity())).append(' ')
                .append(Html.sourceBadge(f.source())).append(' ')
                .append(Html.escape(f.title())).append("</div>");
        if (!f.evidence().isBlank()) {
            sb.append("<div class='field'><b>Evidence:</b> ").append(Html.escape(f.evidence())).append("</div>");
        }
        if (!f.suggestion().isBlank()) {
            sb.append("<div class='field'><b>Suggestion:</b> ").append(Html.escape(f.suggestion())).append("</div>");
        }
        if (!f.alternatives().isEmpty()) {
            sb.append("<div class='field alt'><b>Other valid approaches:</b><ul class='plain'>");
            for (String alt : f.alternatives()) {
                sb.append("<li>").append(Html.escape(alt)).append("</li>");
            }
            sb.append("</ul></div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private Optional<Attempt> findPrevious(String learner, Attempt current) {
        return practiceService.historyForProblem(learner, current.problemId()).stream()
                .filter(a -> a.attemptNumber() == current.attemptNumber() - 1)
                .findFirst();
    }

    public void saveDraft(HttpExchange exchange, Map<String, String> params) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(HttpUtil.readBody(exchange));
        Submission submission = submissionFromForm(form);
        practiceService.saveDraft(params.get("id"), submission);
        HttpUtil.redirect(exchange, "/attempts/" + params.get("id"));
    }

    public void submit(HttpExchange exchange, Map<String, String> params) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(HttpUtil.readBody(exchange));
        Submission submission = submissionFromForm(form);
        practiceService.saveDraft(params.get("id"), submission);
        practiceService.submit(params.get("id"));
        HttpUtil.redirect(exchange, "/attempts/" + params.get("id"));
    }

    public void retry(HttpExchange exchange, Map<String, String> params) throws IOException {
        practiceService.retry(params.get("id"));
        HttpUtil.redirect(exchange, "/attempts/" + params.get("id"));
    }

    public void tryAgain(HttpExchange exchange, Map<String, String> params) throws IOException {
        String learner = Learner.of(exchange);
        Attempt previous = practiceService.getAttempt(params.get("id"));
        Attempt next = practiceService.startAttempt(learner, previous.problemId(), previous.submission());
        HttpUtil.redirect(exchange, "/attempts/" + next.id());
    }

    public void compare(HttpExchange exchange, Map<String, String> params) throws IOException {
        String learner = Learner.of(exchange);
        Map<String, String> query = HttpUtil.queryParams(exchange);
        Attempt current = practiceService.getAttempt(params.get("id"));
        Problem problem = practiceService.getProblem(current.problemId());
        AttemptComparison cmp = practiceService.compare(current.id(), query.get("previous"));

        StringBuilder body = new StringBuilder();
        body.append("<h1>Compare attempts &mdash; ").append(Html.escape(problem.title())).append("</h1>")
                .append("<div class='card'><div class='row'>")
                .append("<div>Previous score: <b>").append(cmp.previousScore()).append("</b></div>")
                .append("<div>Current score: <b>").append(cmp.currentScore()).append("</b></div>")
                .append("<div>Change: <b>").append(cmp.scoreDelta() >= 0 ? "+" : "").append(cmp.scoreDelta()).append("</b></div>")
                .append("</div></div>");

        body.append(comparisonSection("Resolved since last attempt", cmp.resolved()));
        body.append(comparisonSection("Still present", cmp.remaining()));
        body.append(comparisonSection("New since last attempt", cmp.introduced()));

        body.append("<a class='btn secondary' href='/attempts/").append(current.id()).append("'>Back to attempt</a>");
        HttpUtil.sendHtml(exchange, 200, Html.shell("Compare", learner, body.toString()));
    }

    private String comparisonSection(String heading, List<Finding> findings) {
        StringBuilder sb = new StringBuilder("<h2>").append(heading).append(" (").append(findings.size()).append(")</h2>");
        if (findings.isEmpty()) {
            sb.append("<p class='muted'>None.</p>");
        } else {
            for (Finding f : findings) {
                sb.append(renderFinding(f));
            }
        }
        return sb.toString();
    }

    private Submission submissionFromForm(Map<String, String> form) {
        return new Submission(
                form.getOrDefault("assumptions", ""),
                form.getOrDefault("classOutline", ""),
                form.getOrDefault("code", ""),
                form.getOrDefault("tradeOffs", ""),
                SubmissionFormat.parse(form.getOrDefault("format", "JAVA")));
    }
}
