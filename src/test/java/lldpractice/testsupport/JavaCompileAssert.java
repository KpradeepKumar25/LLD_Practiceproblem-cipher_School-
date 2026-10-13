package lldpractice.testsupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Shared helper for tests that need to prove a sample submission's code actually compiles,
 * via {@code javax.tools} rather than just trusting the tolerant extractor parsed it. */
public final class JavaCompileAssert {

    private JavaCompileAssert() {
    }

    public static void assertCompiles(String code) throws Exception {
        String typeName = findPublicTypeName(code);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path outputDir = Files.createTempDirectory("lldpractice-compile-test");
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavaFileObject source = new StringJavaFile(typeName, code);
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics,
                    List.of("-d", outputDir.toString()), null, List.of(source));
            boolean success = task.call();
            List<String> errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString)
                    .toList();
            assertTrue(success && errors.isEmpty(), "Compilation failed:\n" + String.join("\n", errors));
        }
    }

    private static String findPublicTypeName(String code) {
        Matcher m = Pattern.compile("public\\s+(?:final\\s+)?(?:abstract\\s+)?(?:class|interface|enum|record)\\s+(\\w+)")
                .matcher(code);
        if (m.find()) {
            return m.group(1);
        }
        throw new IllegalStateException("No public type found in sample code");
    }

    private static final class StringJavaFile extends SimpleJavaFileObject {
        private final String code;

        StringJavaFile(String name, String code) {
            super(URI.create("string:///" + name + ".java"), JavaFileObject.Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
