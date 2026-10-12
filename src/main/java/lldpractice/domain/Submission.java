package lldpractice.domain;

/**
 * A learner's structured attempt at a design, split into four parts rather than one free-text
 * blob. Each part answers a different evaluation question: assumptions scope the problem,
 * the class outline shows intended responsibilities even before code exists, code gives the
 * deterministic parser something to extract, and trade-offs show the reasoning behind choices
 * that the structure alone can't reveal.
 */
public final class Submission {

    private final String assumptions;
    private final String classOutline;
    private final String code;
    private final String tradeOffs;
    private final SubmissionFormat format;

    public Submission(String assumptions, String classOutline, String code, String tradeOffs,
                       SubmissionFormat format) {
        this.assumptions = assumptions == null ? "" : assumptions;
        this.classOutline = classOutline == null ? "" : classOutline;
        this.code = code == null ? "" : code;
        this.tradeOffs = tradeOffs == null ? "" : tradeOffs;
        this.format = format;
    }

    public String assumptions() {
        return assumptions;
    }

    public String classOutline() {
        return classOutline;
    }

    public String code() {
        return code;
    }

    public String tradeOffs() {
        return tradeOffs;
    }

    public SubmissionFormat format() {
        return format;
    }

    public boolean isBlank() {
        return assumptions.isBlank() && classOutline.isBlank() && code.isBlank() && tradeOffs.isBlank();
    }

    /** Total character count across all four parts, used to enforce a submission size limit. */
    public int totalLength() {
        return assumptions.length() + classOutline.length() + code.length() + tradeOffs.length();
    }
}
