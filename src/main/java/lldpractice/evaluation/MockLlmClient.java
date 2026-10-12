package lldpractice.evaluation;

/**
 * Offline fallback used whenever {@code ANTHROPIC_API_KEY} is not set, so the whole platform
 * works with no API key and no network access. It is a heuristic, not a real reviewer: it looks
 * at coarse signals already visible in the prompt (how much was written in each section) and
 * returns a small, clearly-labelled, conservative JSON response — it never claims CRITICAL or
 * MAJOR severity, because it has no real understanding of the design.
 */
public final class MockLlmClient implements LlmClient {

    @Override
    public String description() {
        return "Mock reviewer (offline heuristic, no ANTHROPIC_API_KEY set)";
    }

    @Override
    public boolean offline() {
        return true;
    }

    @Override
    public String complete(String prompt) {
        String tradeOffs = extractSection(prompt, "Trade-offs:\n", "\n</SUBMISSION_DATA>");
        String outline = extractSection(prompt, "Class outline:\n", "\n\nCode:");

        StringBuilder findings = new StringBuilder();
        boolean any = false;

        if (!tradeOffs.isBlank()) {
            String evidence = snippet(tradeOffs);
            findings.append(any ? "," : "").append("""
                    {"criterionId":"reasoning","severity":"INFO",
                     "title":"Heuristic note on trade-offs",
                     "evidence":"%s",
                     "suggestion":"Consider naming a concrete alternative you rejected and why, to make the trade-off concrete.",
                     "alternatives":["Compare against at least one specific alternative design"]}
                    """.formatted(escape(evidence)));
            any = true;
        }
        if (!outline.isBlank()) {
            String evidence = snippet(outline);
            findings.append(any ? "," : "").append("""
                    {"criterionId":"abstraction","severity":"INFO",
                     "title":"Heuristic note on class outline",
                     "evidence":"%s",
                     "suggestion":"Double-check that each listed class has exactly one clear responsibility.",
                     "alternatives":[]}
                    """.formatted(escape(evidence)));
            any = true;
        }

        String summary = "Offline heuristic review (no live AI model was called). This is a conservative, "
                + "low-confidence pass over what was written, not a full design review.";

        return "{\"summary\":\"" + escape(summary) + "\",\"findings\":[" + findings + "]}";
    }

    private String extractSection(String prompt, String startMarker, String endMarker) {
        int start = prompt.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        start += startMarker.length();
        int end = prompt.indexOf(endMarker, start);
        if (end < 0) {
            end = Math.min(prompt.length(), start + 200);
        }
        return prompt.substring(start, end).trim();
    }

    private String snippet(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 80 ? oneLine.substring(0, 80) + "..." : oneLine;
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
