package lldpractice.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

import lldpractice.catalog.SampleSubmissions;
import lldpractice.domain.Attempt;
import lldpractice.domain.Problem;
import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;
import lldpractice.service.PracticeService;

/** Handlers for browsing problems and starting attempts. */
public final class ProblemHandlers {

    private final PracticeService practiceService;

    public ProblemHandlers(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    public void list(HttpExchange exchange, Map<String, String> params) throws IOException {
        String learner = Learner.of(exchange);
        StringBuilder cards = new StringBuilder("<div class='grid'>");
        for (Problem p : practiceService.listProblems()) {
            cards.append("<div class='card problem-card'>")
                    .append("<h3>").append(Html.escape(p.title())).append("</h3>")
                    .append(Html.difficultyBadge(p.difficulty()))
                    .append("<p class='muted'>").append(Html.escape(p.summary())).append("</p>")
                    .append("<a class='btn' href='/problems/").append(p.id()).append("'>Open</a>")
                    .append("</div>");
        }
        cards.append("</div>");
        String body = "<h1>Practice problems</h1><p class='muted'>Pick one, think before you code, then submit for feedback.</p>"
                + cards;
        HttpUtil.sendHtml(exchange, 200, Html.shell("Problems", learner, body));
    }

    public void detail(HttpExchange exchange, Map<String, String> params) throws IOException {
        String learner = Learner.of(exchange);
        Problem problem = practiceService.getProblem(params.get("id"));

        StringBuilder body = new StringBuilder();
        body.append("<h1>").append(Html.escape(problem.title())).append("</h1>")
                .append(Html.difficultyBadge(problem.difficulty()))
                .append("<p>").append(Html.escape(problem.summary())).append("</p>");

        body.append("<div class='card'><h2 style='margin-top:0'>Requirements</h2><ul class='plain'>");
        for (String r : problem.requirements()) {
            body.append("<li>").append(Html.escape(r)).append("</li>");
        }
        body.append("</ul></div>");

        body.append("<div class='card'><h2 style='margin-top:0'>Think before you design</h2><ul class='plain'>");
        for (String t : problem.thinkingPrompts()) {
            body.append("<li>").append(Html.escape(t)).append("</li>");
        }
        body.append("</ul></div>");

        body.append("<div class='card'><h2 style='margin-top:0'>Hints</h2><ul class='plain'>");
        for (String h : problem.hints()) {
            body.append("<li>").append(Html.escape(h)).append("</li>");
        }
        body.append("</ul></div>");

        body.append("<form method='post' action='/problems/").append(problem.id()).append("/attempts'>")
                .append("<button class='btn' type='submit'>Start a new attempt</button></form>");

        if (problem.id().equals("parking-lot")) {
            body.append("<p class='muted' style='margin-top:14px'>Want to see the loop in action first? ")
                    .append("<a href='/problems/parking-lot/prefill/weak'>Prefill a weak example</a> or ")
                    .append("<a href='/problems/parking-lot/prefill/good-a'>a strong example (Strategy interfaces)</a> or ")
                    .append("<a href='/problems/parking-lot/prefill/good-b'>a strong example (abstract class + records)</a>.</p>");
        } else if (problem.id().equals("vending-machine")) {
            body.append("<p class='muted' style='margin-top:14px'>Want to see the loop in action first? ")
                    .append("<a href='/problems/vending-machine/prefill/weak'>Prefill a weak example</a> or ")
                    .append("<a href='/problems/vending-machine/prefill/good-a'>a strong example (Payment strategy interface)</a>.</p>");
        }

        List<Attempt> history = practiceService.historyForProblem(learner, problem.id());
        if (!history.isEmpty()) {
            body.append("<h2>Your attempts at this problem</h2><div class='card'>");
            for (Attempt a : history) {
                body.append("<div class='row' style='justify-content:space-between;padding:6px 0'>")
                        .append("<span>Attempt #").append(a.attemptNumber()).append(" ")
                        .append(Html.statusBadge(a.status())).append("</span>")
                        .append("<span>").append(a.feedback() == null ? "" : "Score: " + a.feedback().overallScore())
                        .append(" <a href='/attempts/").append(a.id()).append("'>View</a></span>")
                        .append("</div>");
            }
            body.append("</div>");
        }

        HttpUtil.sendHtml(exchange, 200, Html.shell(problem.title(), learner, body.toString()));
    }

    public void startAttempt(HttpExchange exchange, Map<String, String> params) throws IOException {
        String learner = Learner.of(exchange);
        Attempt attempt = practiceService.startAttempt(learner, params.get("id"), null);
        HttpUtil.redirect(exchange, "/attempts/" + attempt.id());
    }

    public void prefill(HttpExchange exchange, Map<String, String> params) throws IOException {
        String learner = Learner.of(exchange);
        String problemId = params.get("id");
        String kind = params.get("kind");
        Submission submission = switch (problemId + ":" + kind) {
            case "parking-lot:weak" -> SampleSubmissions.weak();
            case "parking-lot:good-a" -> SampleSubmissions.strongStrategyBased();
            case "parking-lot:good-b" -> SampleSubmissions.strongRecordBased();
            case "vending-machine:weak" -> SampleSubmissions.vendingWeak();
            case "vending-machine:good-a" -> SampleSubmissions.vendingStrong();
            default -> new Submission("", "", "", "", SubmissionFormat.JAVA);
        };
        Attempt attempt = practiceService.startAttempt(learner, problemId, submission);
        HttpUtil.redirect(exchange, "/attempts/" + attempt.id());
    }
}
