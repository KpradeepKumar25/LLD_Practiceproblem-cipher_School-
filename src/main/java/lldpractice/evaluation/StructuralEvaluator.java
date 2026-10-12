package lldpractice.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lldpractice.design.DesignModel;
import lldpractice.design.MethodModel;
import lldpractice.design.TypeKind;
import lldpractice.design.TypeModel;
import lldpractice.domain.Concept;
import lldpractice.domain.Finding;
import lldpractice.domain.FindingSource;
import lldpractice.domain.Problem;
import lldpractice.domain.Severity;
import lldpractice.domain.Submission;

/**
 * The deterministic, essential evaluator. Everything here is a reproducible rule over the
 * {@link DesignModel}: the same submission always produces the same findings. It owns
 * structure, completeness against expected {@link Concept}s, and well-known smells
 * (god classes, long methods, instanceof/switch chains, public mutable fields, unused
 * interfaces, anemic classes). Judgement calls that need reasoning — whether a responsibility
 * split actually makes sense, whether a trade-off argument holds up — are deliberately left to
 * {@link LlmEvaluator}.
 */
public final class StructuralEvaluator implements Evaluator {

    private static final int GOD_CLASS_METHODS = 7;
    private static final int GOD_CLASS_FIELDS = 5;
    private static final int LONG_METHOD_MAJOR_LINES = 40;
    private static final int LONG_METHOD_MINOR_LINES = 20;
    private static final int HIGH_COUPLING_THRESHOLD = 6;
    private static final int INSTANCEOF_CHAIN_THRESHOLD = 2;
    private static final int EQUALS_CHAIN_THRESHOLD = 4;
    private static final int THIN_TEXT_CHARS = 40;

    @Override
    public String name() {
        return "Rule-based structural review";
    }

    @Override
    public boolean essential() {
        return true;
    }

    @Override
    public List<Finding> evaluate(EvaluationContext context) {
        List<Finding> findings = new ArrayList<>();
        Problem problem = context.problem();
        Submission submission = context.submission();
        DesignModel model = context.designModel();

        evaluateRequirementCoverage(problem, submission, model, findings);
        evaluateAbstractions(model, findings);
        evaluateResponsibility(model, findings);
        evaluateExtensibility(model, submission, findings);
        evaluateEncapsulation(model, findings);
        evaluateReasoning(submission, findings);

        return findings;
    }

    // ---- requirement coverage -------------------------------------------------

    private void evaluateRequirementCoverage(Problem problem, Submission submission, DesignModel model,
                                               List<Finding> findings) {
        int covered = 0;
        int total = problem.concepts().size();
        for (Concept concept : problem.concepts()) {
            boolean inCode = matchesInDesignModel(concept, model);
            boolean inOutline = concept.matches(submission.classOutline()) || concept.matches(submission.assumptions());
            if (inCode) {
                covered++;
            } else if (inOutline) {
                findings.add(new Finding("requirement_coverage", Severity.MINOR,
                        "Concept described but not implemented: " + concept.label(),
                        "The outline/assumptions mention this, but no matching class, method or field was found in the code.",
                        "Turn this planned idea into an actual type or method so it can be evaluated and reused.",
                        List.of("Add a small class or method named for " + concept.label() + " using any of: "
                                + String.join(", ", concept.acceptedNames())),
                        FindingSource.DETERMINISTIC));
            } else if (concept.required()) {
                findings.add(new Finding("requirement_coverage", Severity.MAJOR,
                        "Missing expected concept: " + concept.label(),
                        "No class, method, field or outline text matched any of: " + String.join(", ", concept.acceptedNames()) + ".",
                        "This is one of the requirements for this problem; add a type or behaviour for it.",
                        List.of("Any of these names would count: " + String.join(", ", concept.acceptedNames())),
                        FindingSource.DETERMINISTIC));
            }
        }
        if (total > 0 && covered == total) {
            findings.add(new Finding("requirement_coverage", Severity.POSITIVE,
                    "All expected concepts are represented in the code",
                    covered + " of " + total + " expected concepts were found as real types, methods or fields.",
                    "", List.of(), FindingSource.DETERMINISTIC));
        }
    }

    private boolean matchesInDesignModel(Concept concept, DesignModel model) {
        for (TypeModel type : model.types()) {
            if (concept.matches(type.name())) {
                return true;
            }
            for (String supertype : type.supertypes()) {
                if (concept.matches(supertype)) {
                    return true;
                }
            }
            for (String dep : type.dependencies()) {
                if (concept.matches(dep)) {
                    return true;
                }
            }
            for (MethodModel method : type.methods()) {
                if (concept.matches(method.name())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- abstractions -------------------------------------------------

    private void evaluateAbstractions(DesignModel model, List<Finding> findings) {
        for (TypeModel type : model.types()) {
            if (type.kind() == TypeKind.INTERFACE || type.kind() == TypeKind.ABSTRACT_CLASS) {
                boolean implemented = model.types().stream()
                        .anyMatch(other -> other != type && other.supertypes().stream()
                                .anyMatch(s -> s.equalsIgnoreCase(type.name())));
                if (!implemented) {
                    findings.add(new Finding("abstraction", Severity.MINOR,
                            "Unused abstraction: " + type.name(),
                            type.kind() + " '" + type.name() + "' is declared but no other type extends or implements it.",
                            "Either implement it with at least one concrete type, or remove it if it isn't needed yet.",
                            List.of("If this is meant as a future extension point, add one concrete implementation now to prove the seam works."),
                            FindingSource.DETERMINISTIC));
                }
            }
        }
        boolean anySharedSupertype = model.types().stream()
                .anyMatch(type -> model.types().stream()
                        .anyMatch(other -> other != type && other.supertypes().stream()
                                .anyMatch(s -> s.equalsIgnoreCase(type.name()))));
        if (!model.types().isEmpty() && !anySharedSupertype) {
            findings.add(new Finding("abstraction", Severity.MAJOR,
                    "No shared abstraction between types",
                    "None of the submitted types are extended or implemented by another type, so variation "
                            + "(e.g. vehicle type, pricing scheme) has no common supertype to hang behaviour on.",
                    "Introduce an interface or abstract class capturing what varies, and have concrete variants extend/implement it.",
                    List.of("An interface with one method per variation point (Strategy)",
                            "An abstract base class with a template method"),
                    FindingSource.DETERMINISTIC));
        }
        boolean anyUnused = findings.stream().anyMatch(f -> f.criterionId().equals("abstraction") && f.severity() != Severity.POSITIVE);
        if (anySharedSupertype && !anyUnused) {
            findings.add(new Finding("abstraction", Severity.POSITIVE,
                    "Interfaces/abstract classes are used and actually implemented",
                    "At least one interface or abstract class has a concrete implementation, showing a real extension point rather than a declared-but-unused one.",
                    "", List.of(), FindingSource.DETERMINISTIC));
        }
    }

    // ---- responsibility -------------------------------------------------

    private void evaluateResponsibility(DesignModel model, List<Finding> findings) {
        boolean anyMajor = false;
        for (TypeModel type : model.types()) {
            int methodCount = type.methods().size();
            if (methodCount >= GOD_CLASS_METHODS && type.fieldCount() >= GOD_CLASS_FIELDS) {
                findings.add(new Finding("responsibility", Severity.MAJOR,
                        "Possible god class: " + type.name(),
                        type.name() + " has " + type.fieldCount() + " fields and " + methodCount + " methods.",
                        "Split this into smaller classes, each owning one responsibility.",
                        List.of("Extract cohesive groups of fields/methods into their own classes (e.g. pricing, allocation, notification)."),
                        FindingSource.DETERMINISTIC));
                anyMajor = true;
            }
            String lowerName = type.name().toLowerCase(Locale.ROOT);
            if ((lowerName.contains("manager") || lowerName.contains("util") || lowerName.contains("helper"))
                    && (type.fieldCount() + methodCount) > 3) {
                findings.add(new Finding("responsibility", Severity.MINOR,
                        "Generic role name: " + type.name(),
                        "'" + type.name() + "' is named like a catch-all container rather than a specific responsibility.",
                        "Name the class after what it actually does.",
                        List.of("e.g. PricingCalculator, SpotAllocator, NotificationSender instead of a generic Manager/Util"),
                        FindingSource.DETERMINISTIC));
            }
            for (MethodModel method : type.methods()) {
                if (method.bodyLineCount() > LONG_METHOD_MAJOR_LINES) {
                    findings.add(new Finding("responsibility", Severity.MAJOR,
                            "Long method: " + type.name() + "#" + method.name(),
                            method.name() + " is about " + method.bodyLineCount() + " lines long.",
                            "Extract smaller, named helper methods so each piece of logic is easy to read and test.",
                            List.of(), FindingSource.DETERMINISTIC));
                    anyMajor = true;
                } else if (method.bodyLineCount() > LONG_METHOD_MINOR_LINES) {
                    findings.add(new Finding("responsibility", Severity.MINOR,
                            "Method getting long: " + type.name() + "#" + method.name(),
                            method.name() + " is about " + method.bodyLineCount() + " lines long.",
                            "Consider extracting part of this into a helper method.",
                            List.of(), FindingSource.DETERMINISTIC));
                }
            }
            if (type.kind() == TypeKind.CLASS && type.fieldCount() > 0 && type.methods().isEmpty()) {
                findings.add(new Finding("responsibility", Severity.INFO,
                        "Anemic class: " + type.name(),
                        type.name() + " has " + type.fieldCount() + " field(s) but no behaviour methods.",
                        "If logic about this data lives elsewhere, consider moving it here so the class does something, not just holds data.",
                        List.of(), FindingSource.DETERMINISTIC));
            }
        }
        if (!anyMajor && model.types().size() > 1) {
            findings.add(new Finding("responsibility", Severity.POSITIVE,
                    "Responsibilities look reasonably split",
                    "No single class dominates with an excessive number of fields and methods across the " + model.types().size() + " types submitted.",
                    "", List.of(), FindingSource.DETERMINISTIC));
        }
    }

    // ---- extensibility -------------------------------------------------

    private void evaluateExtensibility(DesignModel model, Submission submission, List<Finding> findings) {
        boolean anyMajor = false;
        for (TypeModel type : model.types()) {
            int instanceofCount = type.instanceofCount();
            if (instanceofCount > INSTANCEOF_CHAIN_THRESHOLD) {
                findings.add(new Finding("extensibility", Severity.MAJOR,
                        "Type-checking via instanceof: " + type.name(),
                        type.name() + " uses 'instanceof' " + instanceofCount + " time(s).",
                        "Replace the instanceof chain with a method on a shared interface and let each subtype implement it.",
                        List.of("Polymorphic dispatch (one method per subtype)", "Visitor pattern if the operations vary independently of the types"),
                        FindingSource.DETERMINISTIC));
                anyMajor = true;
            }
            int switchCount = type.switchCount();
            if (switchCount > 1) {
                findings.add(new Finding("extensibility", Severity.MINOR,
                        "Multiple switch statements: " + type.name(),
                        type.name() + " contains " + switchCount + " switch statement(s).",
                        "A switch on a type/category is a common sign that a new case means editing this class again.",
                        List.of("Strategy objects keyed by the category", "Polymorphism via a common interface"),
                        FindingSource.DETERMINISTIC));
            }
            if (type.dependencies().size() > HIGH_COUPLING_THRESHOLD) {
                findings.add(new Finding("extensibility", Severity.MAJOR,
                        "High coupling: " + type.name(),
                        type.name() + " directly depends on " + type.dependencies().size() + " other types: "
                                + String.join(", ", type.dependencies()) + ".",
                        "Consider depending on fewer, higher-level abstractions, or delegating some of this to collaborator classes.",
                        List.of(), FindingSource.DETERMINISTIC));
                anyMajor = true;
            }
        }
        int equalsChains = countOccurrences(submission.code(), ".equals(");
        if (equalsChains > EQUALS_CHAIN_THRESHOLD) {
            findings.add(new Finding("extensibility", Severity.MINOR,
                    "Repeated equality checks suggest hard-coded variation",
                    "Found " + equalsChains + " '.equals(' calls, often a sign of if/else-if chains comparing against known values.",
                    "A lookup table or a small set of Strategy objects usually scales better than repeated equality checks.",
                    List.of(), FindingSource.DETERMINISTIC));
        }
        if (!anyMajor && !model.types().isEmpty()) {
            findings.add(new Finding("extensibility", Severity.POSITIVE,
                    "No instanceof/switch-chain or high-coupling smells detected",
                    "The submitted types avoid heavy type-checking and excessive direct dependencies.",
                    "", List.of(), FindingSource.DETERMINISTIC));
        }
    }

    // ---- encapsulation -------------------------------------------------

    private void evaluateEncapsulation(DesignModel model, List<Finding> findings) {
        boolean anyIssue = false;
        for (TypeModel type : model.types()) {
            if (type.publicMutableFieldCount() > 0) {
                findings.add(new Finding("encapsulation", Severity.MAJOR,
                        "Public mutable field(s) in " + type.name(),
                        type.name() + " exposes " + type.publicMutableFieldCount() + " public, non-final field(s).",
                        "Make fields private and expose behaviour (methods) instead of raw mutable state.",
                        List.of("private field + getter (and setter only if mutation is actually needed)",
                                "an immutable value object / record if the field never needs to change after construction"),
                        FindingSource.DETERMINISTIC));
                anyIssue = true;
            }
        }
        if (!anyIssue && !model.types().isEmpty()) {
            findings.add(new Finding("encapsulation", Severity.POSITIVE,
                    "No public mutable fields detected",
                    "Every submitted type keeps its state private or immutable.",
                    "", List.of(), FindingSource.DETERMINISTIC));
        }
    }

    // ---- reasoning -------------------------------------------------

    private void evaluateReasoning(Submission submission, List<Finding> findings) {
        checkThinText(submission.assumptions(), "Assumptions", findings);
        checkThinText(submission.tradeOffs(), "Trade-offs", findings);
        if (submission.assumptions().length() >= THIN_TEXT_CHARS && submission.tradeOffs().length() >= THIN_TEXT_CHARS) {
            findings.add(new Finding("reasoning", Severity.POSITIVE,
                    "Assumptions and trade-offs are both explained",
                    "Both sections go beyond a one-line placeholder, giving real reasoning to evaluate.",
                    "", List.of(), FindingSource.DETERMINISTIC));
        }
    }

    private void checkThinText(String text, String sectionName, List<Finding> findings) {
        if (text.isBlank()) {
            findings.add(new Finding("reasoning", Severity.MAJOR,
                    sectionName + " section is empty",
                    "No " + sectionName.toLowerCase(Locale.ROOT) + " were provided.",
                    "Explain your " + sectionName.toLowerCase(Locale.ROOT) + " so the feedback can judge your reasoning, not just your code.",
                    List.of(), FindingSource.DETERMINISTIC));
        } else if (text.length() < THIN_TEXT_CHARS) {
            findings.add(new Finding("reasoning", Severity.MINOR,
                    sectionName + " section is very thin",
                    "Only " + text.length() + " characters were provided.",
                    "A sentence or two of real reasoning is far more useful than a placeholder.",
                    List.of(), FindingSource.DETERMINISTIC));
        }
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
