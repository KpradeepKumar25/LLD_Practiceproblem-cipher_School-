package lldpractice.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;

class JavaDesignExtractorTest {

    private final JavaDesignExtractor extractor = new JavaDesignExtractor();

    private DesignModel extract(String code) {
        return extractor.extract(new Submission("", "", code, "", SubmissionFormat.JAVA));
    }

    @Test
    void detectsNestedTypes() {
        DesignModel model = extract("""
                public class Outer {
                    static class Inner {
                        int x;
                    }
                }
                """);
        assertEquals(2, model.types().size());
        TypeModel outer = model.types().stream().filter(t -> t.name().equals("Outer")).findFirst().orElseThrow();
        TypeModel inner = model.types().stream().filter(t -> t.name().equals("Inner")).findFirst().orElseThrow();
        assertFalse(outer.nested());
        assertTrue(inner.nested());
    }

    @Test
    void bracesInsideCommentsAndStringsDoNotConfuseParsing() {
        DesignModel model = extract("""
                public class Weird {
                    // a comment with a brace {
                    /* a block comment with { and } */
                    String s = "a string with { and }";
                    public int realField;

                    public void method() {
                        System.out.println("another { trap }");
                    }
                }
                """);
        assertEquals(0, model.warnings().size());
        TypeModel weird = model.types().get(0);
        assertEquals(1, weird.methods().size());
        assertEquals(2, weird.fieldCount());
    }

    @Test
    void unbalancedBracesProduceWarningsInsteadOfCrashing() {
        DesignModel model = extract("""
                public class Broken {
                    public void method() {
                        if (true) {
                            System.out.println("missing closing braces");
                    }
                """);
        assertTrue(model.warnings().stream().anyMatch(w -> w.contains("unclosed")));
    }

    @Test
    void completelyGarbledInputDoesNotThrow() {
        DesignModel model = extract("}}} class {{{ ??? this is not java at all ((( }}}");
        assertTrue(model.warnings().size() >= 0); // just asserting no exception was thrown
    }

    @Test
    void tracksInstanceofAndSwitchCounts() {
        DesignModel model = extract("""
                public class Checker {
                    public void check(Object o) {
                        if (o instanceof String) {
                            return;
                        }
                        if (o instanceof Integer) {
                            return;
                        }
                        switch (o.toString()) {
                            case "a": break;
                            default: break;
                        }
                    }
                }
                """);
        MethodModel method = model.types().get(0).methods().get(0);
        assertEquals(2, method.instanceofCount());
        assertEquals(1, method.switchCount());
    }

    @Test
    void countsPublicMutableFieldsOnly() {
        DesignModel model = extract("""
                public class Fields {
                    public int mutable;
                    public final int immutable = 1;
                    private int hidden;
                }
                """);
        TypeModel type = model.types().get(0);
        assertEquals(3, type.fieldCount());
        assertEquals(1, type.publicMutableFieldCount());
    }

    @Test
    void detectsSupertypesForExtendsAndImplements() {
        DesignModel model = extract("""
                interface Shape {}
                class Circle implements Shape {}
                """);
        TypeModel circle = model.types().stream().filter(t -> t.name().equals("Circle")).findFirst().orElseThrow();
        assertTrue(circle.supertypes().contains("Shape"));
    }
}
