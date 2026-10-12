package lldpractice.web;

import lldpractice.domain.AttemptStatus;
import lldpractice.domain.Difficulty;
import lldpractice.domain.FindingSource;
import lldpractice.domain.Severity;

/**
 * HTML escaping and the shared page shell/components. No templating engine: with ~10 pages,
 * plain Java string building stays more readable than pulling one in. Every piece of
 * learner-controlled or LLM-controlled text must go through {@link #escape(String)} before
 * being embedded — this is what keeps a learner's (or a prompt-injected LLM's) text from being
 * interpreted as HTML/JS.
 */
public final class Html {

    private Html() {
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final String CSS = """
            :root {
              --bg: #f6f7fb; --card: #ffffff; --text: #1f2430; --muted: #6b7280;
              --border: #e4e7ef; --accent: #4f46e5; --accent-dark: #3730a3;
              --critical: #b91c1c; --major: #c2410c; --minor: #a16207;
              --info: #2563eb; --positive: #15803d;
            }
            * { box-sizing: border-box; }
            body { margin: 0; font-family: -apple-system, Segoe UI, Roboto, Arial, sans-serif;
                   background: var(--bg); color: var(--text); line-height: 1.5; }
            nav { background: var(--accent-dark); color: white; padding: 14px 24px;
                  display: flex; align-items: center; gap: 20px; flex-wrap: wrap; }
            nav a { color: white; text-decoration: none; opacity: 0.9; font-weight: 500; }
            nav a:hover { opacity: 1; text-decoration: underline; }
            nav .brand { font-weight: 700; font-size: 1.1em; margin-right: 10px; }
            nav form { margin-left: auto; display: flex; gap: 6px; align-items: center; }
            nav input[type=text] { padding: 6px 8px; border-radius: 6px; border: none; width: 140px; }
            nav button, .btn { background: var(--accent); color: white; border: none;
                   padding: 8px 14px; border-radius: 6px; cursor: pointer; font-size: 0.95em; text-decoration: none;
                   display: inline-block; }
            nav button:hover, .btn:hover { background: var(--accent-dark); }
            .btn.secondary { background: #e5e7eb; color: var(--text); }
            .btn.secondary:hover { background: #d1d5db; }
            main { max-width: 960px; margin: 0 auto; padding: 28px 20px 60px; }
            h1 { font-size: 1.6em; margin: 0 0 4px; }
            h2 { font-size: 1.2em; margin: 28px 0 10px; }
            .card { background: var(--card); border: 1px solid var(--border); border-radius: 10px;
                    padding: 20px; margin-bottom: 16px; }
            .muted { color: var(--muted); }
            .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
            .problem-card h3 { margin: 0 0 6px; }
            .badge { display: inline-block; padding: 2px 9px; border-radius: 999px; font-size: 0.78em;
                     font-weight: 600; color: white; }
            .badge.EASY, .badge.POSITIVE { background: var(--positive); }
            .badge.MEDIUM, .badge.INFO { background: var(--info); }
            .badge.HARD, .badge.MAJOR { background: var(--major); }
            .badge.CRITICAL { background: var(--critical); }
            .badge.MINOR { background: var(--minor); }
            .badge.DETERMINISTIC { background: #334155; }
            .badge.LLM { background: #7c3aed; }
            .status { display: inline-block; padding: 4px 12px; border-radius: 999px; font-weight: 700;
                      font-size: 0.85em; color: white; }
            .status.DRAFT { background: #9ca3af; }
            .status.SUBMITTED, .status.EVALUATING { background: #2563eb; }
            .status.COMPLETED { background: var(--positive); }
            .status.PARTIALLY_COMPLETED { background: var(--minor); }
            .status.FAILED { background: var(--critical); }
            textarea, input[type=text] { font-family: ui-monospace, Consolas, monospace; font-size: 0.92em; }
            textarea { width: 100%; border: 1px solid var(--border); border-radius: 8px; padding: 10px;
                       resize: vertical; }
            label { display: block; font-weight: 600; margin: 14px 0 6px; }
            label .hint { font-weight: 400; color: var(--muted); font-size: 0.85em; }
            .finding { border-left: 4px solid var(--border); padding: 10px 14px; margin: 10px 0;
                       background: #fafafa; border-radius: 0 8px 8px 0; }
            .finding.CRITICAL { border-color: var(--critical); }
            .finding.MAJOR { border-color: var(--major); }
            .finding.MINOR { border-color: var(--minor); }
            .finding.INFO { border-color: var(--info); }
            .finding.POSITIVE { border-color: var(--positive); }
            .finding .title { font-weight: 700; margin-bottom: 4px; }
            .finding .field { margin-top: 6px; font-size: 0.92em; }
            .finding .field b { color: var(--muted); font-weight: 600; }
            .score-row { display: flex; align-items: center; gap: 10px; margin: 6px 0; }
            .score-row .label { width: 180px; font-size: 0.9em; }
            .score-bar { flex: 1; height: 10px; background: #e5e7eb; border-radius: 6px; overflow: hidden; }
            .score-bar > div { height: 100%; background: var(--accent); }
            .score-num { width: 40px; text-align: right; font-weight: 700; font-size: 0.9em; }
            .overall { font-size: 2.2em; font-weight: 800; }
            .notice { background: #fff7ed; border: 1px solid #fed7aa; color: #9a3412; padding: 10px 14px;
                      border-radius: 8px; margin-bottom: 14px; }
            .alt { font-size: 0.88em; color: var(--muted); }
            ul.plain { margin: 4px 0; padding-left: 20px; }
            .row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
            """;

    public static String shell(String title, String learnerName, String bodyContent) {
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + escape(title) + " - LLD Practice Platform</title>"
                + "<style>" + CSS + "</style></head><body>"
                + nav(learnerName)
                + "<main>" + bodyContent + "</main>"
                + "</body></html>";
    }

    private static String nav(String learnerName) {
        return "<nav>"
                + "<span class='brand'>LLD Practice</span>"
                + "<a href='/problems'>Problems</a>"
                + "<a href='/history'>My history</a>"
                + "<form method='post' action='/learner'>"
                + "<input type='hidden' name='redirectTo' value=''>"
                + "<input type='text' name='name' placeholder='Your name' value='" + escape(learnerName) + "'>"
                + "<button type='submit'>Save</button>"
                + "</form>"
                + "</nav>";
    }

    public static String errorPage(int status, String title, String message) {
        return shell(title, "", "<div class='card'><h1>" + status + " " + escape(title) + "</h1>"
                + "<p>" + escape(message) + "</p><p><a class='btn secondary' href='/problems'>Back to problems</a></p></div>");
    }

    public static String difficultyBadge(Difficulty d) {
        return "<span class='badge " + d.name() + "'>" + d.name() + "</span>";
    }

    public static String statusBadge(AttemptStatus status) {
        return "<span class='status " + status.name() + "'>" + prettyStatus(status) + "</span>";
    }

    public static String prettyStatus(AttemptStatus status) {
        return switch (status) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
            case EVALUATING -> "Evaluating...";
            case COMPLETED -> "Completed";
            case PARTIALLY_COMPLETED -> "Partially completed";
            case FAILED -> "Failed";
        };
    }

    public static String severityBadge(Severity severity) {
        return "<span class='badge " + severity.name() + "'>" + severity.name() + "</span>";
    }

    public static String sourceBadge(FindingSource source) {
        String label = source == FindingSource.DETERMINISTIC ? "Rule-based" : "AI review";
        return "<span class='badge " + source.name() + "'>" + label + "</span>";
    }

    public static String scoreBar(String label, int score) {
        return "<div class='score-row'><div class='label'>" + escape(label) + "</div>"
                + "<div class='score-bar'><div style='width:" + Math.max(0, Math.min(100, score)) + "%'></div></div>"
                + "<div class='score-num'>" + score + "</div></div>";
    }
}
