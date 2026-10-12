package lldpractice.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

import lldpractice.domain.Attempt;
import lldpractice.service.PracticeService;

/** The learner's attempt history across every problem. */
public final class HistoryHandlers {

    private final PracticeService practiceService;

    public HistoryHandlers(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    public void view(HttpExchange exchange, Map<String, String> params) throws IOException {
        String learner = Learner.of(exchange);
        List<Attempt> attempts = practiceService.history(learner);

        StringBuilder body = new StringBuilder();
        body.append("<h1>Your history</h1><p class='muted'>Signed in as ").append(Html.escape(learner)).append("</p>");

        if (attempts.isEmpty()) {
            body.append("<div class='card'><p>No attempts yet. <a href='/problems'>Pick a problem</a> to get started.</p></div>");
        } else {
            body.append("<div class='card'>");
            for (Attempt a : attempts) {
                String problemTitle;
                try {
                    problemTitle = practiceService.getProblem(a.problemId()).title();
                } catch (RuntimeException e) {
                    problemTitle = a.problemId();
                }
                body.append("<div class='row' style='justify-content:space-between;padding:8px 0;border-bottom:1px solid var(--border)'>")
                        .append("<span><b>").append(Html.escape(problemTitle)).append("</b> &middot; Attempt #")
                        .append(a.attemptNumber()).append(' ').append(Html.statusBadge(a.status())).append("</span>")
                        .append("<span>").append(a.feedback() == null ? "" : "Score: " + a.feedback().overallScore() + " &middot; ")
                        .append("<a href='/attempts/").append(a.id()).append("'>View</a></span>")
                        .append("</div>");
            }
            body.append("</div>");
        }
        HttpUtil.sendHtml(exchange, 200, Html.shell("History", learner, body.toString()));
    }
}
