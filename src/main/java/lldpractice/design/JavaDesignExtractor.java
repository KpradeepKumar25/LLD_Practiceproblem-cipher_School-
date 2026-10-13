package lldpractice.design;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lldpractice.domain.Submission;
import lldpractice.domain.SubmissionFormat;

/**
 * A lightweight, tolerant parser for Java-shaped source: not a compiler front end, but enough
 * structure (types, supertypes, methods, fields, {@code instanceof}/{@code switch} usage,
 * dependencies) to let {@code StructuralEvaluator} reason about a design regardless of whether
 * the code actually compiles. Comments and string/char literals are stripped first so braces
 * inside them never confuse the brace matcher; malformed input degrades to warnings rather
 * than exceptions.
 */
public final class JavaDesignExtractor implements DesignExtractor {

    private static final Set<String> COMMON_JDK_TYPES = Set.of(
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte", "Short",
            "Object", "List", "Map", "Set", "Collection", "ArrayList", "HashMap", "LinkedHashMap",
            "HashSet", "LinkedList", "Optional", "Comparator", "Override", "Exception",
            "RuntimeException", "IllegalArgumentException", "IllegalStateException", "Void",
            "Number", "Iterable", "Iterator", "Stream", "Arrays", "Collections", "UUID", "Instant",
            "LocalDate", "LocalDateTime", "Thread", "Runnable"
    );

    private static final Pattern TYPE_DECL = Pattern.compile(
            "(?:public|private|protected)?\\s*(?:static\\s+)?(?:final\\s+)?(?:(abstract)\\s+)?"
                    + "(class|interface|enum|record)\\s+([A-Za-z_]\\w*)"
                    + "(?:\\s*<[^>{]*>)?"
                    + "(?:\\s*\\([^)]*\\))?"
                    + "(?:\\s+extends\\s+([\\w.<>,\\s]+?))?"
                    + "(?:\\s+implements\\s+([\\w.<>,\\s]+?))?"
                    + "\\s*\\{"
    );

    private static final Pattern METHOD_DECL = Pattern.compile(
            // The leading \s* (unconditional, not tied to the optional modifier group) matters:
            // without it, Matcher.find()'s leftmost match starts at the indentation whitespace
            // itself, the optional modifier group gets evaluated against that whitespace and
            // locks in as empty, and "public"/"private"/"protected" silently gets absorbed into
            // the return-type group instead -- meaning isPublic() was wrong for every indented
            // method (i.e. almost all real code) until this was added.
            "\\s*(public|private|protected)?\\s*(static\\s+)?(?:final\\s+)?(?:abstract\\s+)?(?:synchronized\\s+)?"
                    + "(?:<[^>{]*>\\s*)?"
                    + "([A-Za-z_][\\w.\\[\\]<>,\\s]*?)\\s+([A-Za-z_]\\w*)\\s*\\(([^)]*)\\)\\s*"
                    + "(?:throws\\s+[\\w.,\\s]+)?\\s*\\{"
    );

    private static final Pattern FIELD_DECL = Pattern.compile(
            "(?m)^[ \\t]*(public|private|protected)?[ \\t]*(static\\s+)?(final\\s+)?"
                    + "([A-Za-z_][\\w.<>\\[\\],\\s]*?)\\s+([A-Za-z_]\\w*)\\s*(=[^;{]*)?;"
    );

    @Override
    public SubmissionFormat format() {
        return SubmissionFormat.JAVA;
    }

    @Override
    public DesignModel extract(Submission submission) {
        List<String> warnings = new ArrayList<>();
        String cleaned = stripCommentsAndLiterals(submission.code());
        checkBraceBalance(cleaned, warnings);

        List<RawType> rawTypes = findTypeDeclarations(cleaned, warnings);
        List<TypeModel> models = new ArrayList<>();
        for (RawType rt : rawTypes) {
            boolean nested = rawTypes.stream()
                    .anyMatch(other -> other != rt && other.braceIndex < rt.declStart && rt.bodyEnd <= other.bodyEnd);
            String rawBody = safeSubstring(cleaned, rt.braceIndex + 1, rt.bodyEnd);
            String bodyNoNested = maskNestedSpans(rawBody, rawTypes, rt);

            List<FoundMethod> methods = findMethods(bodyNoNested);
            String bodyNoMethods = maskSpans(bodyNoNested, methods.stream().map(FoundMethod::span).toList());
            FieldStats fields = findFields(bodyNoMethods);
            List<String> dependencies = collectDependencies(fields.fieldTypes, methods);

            models.add(new TypeModel(rt.name, rt.kind, rt.supertypes,
                    methods.stream().map(FoundMethod::model).toList(),
                    fields.count, fields.publicMutable, dependencies, nested));
        }
        return new DesignModel(models, warnings);
    }

    // ---- type discovery -------------------------------------------------

    private List<RawType> findTypeDeclarations(String cleaned, List<String> warnings) {
        List<RawType> result = new ArrayList<>();
        Matcher m = TYPE_DECL.matcher(cleaned);
        while (m.find()) {
            String kindWord = m.group(2);
            boolean isAbstract = m.group(1) != null;
            TypeKind kind = switch (kindWord) {
                case "interface" -> TypeKind.INTERFACE;
                case "enum" -> TypeKind.ENUM;
                case "record" -> TypeKind.RECORD;
                default -> isAbstract ? TypeKind.ABSTRACT_CLASS : TypeKind.CLASS;
            };
            String name = m.group(3);
            int braceIndex = m.end() - 1;
            int bodyEnd = findMatchingBrace(cleaned, braceIndex);
            if (bodyEnd < 0) {
                warnings.add("Could not find the closing brace for type '" + name + "'; its members may be incomplete.");
                bodyEnd = cleaned.length() - 1;
            }
            List<String> supertypes = new ArrayList<>();
            supertypes.addAll(splitTypeList(m.group(4)));
            supertypes.addAll(splitTypeList(m.group(5)));
            result.add(new RawType(name, kind, supertypes, m.start(), braceIndex, bodyEnd));
        }
        return result;
    }

    private List<String> splitTypeList(String group) {
        if (group == null || group.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : group.split(",")) {
            String token = part.trim();
            int generic = token.indexOf('<');
            if (generic >= 0) {
                token = token.substring(0, generic);
            }
            if (!token.isBlank()) {
                out.add(token.trim());
            }
        }
        return out;
    }

    // ---- method discovery -------------------------------------------------

    private record FoundMethod(MethodModel model, int[] span) {
    }

    private List<FoundMethod> findMethods(String body) {
        List<FoundMethod> found = new ArrayList<>();
        Matcher m = METHOD_DECL.matcher(body);
        int searchFrom = 0;
        while (searchFrom < body.length() && m.find(searchFrom)) {
            boolean isPublic = "public".equals(m.group(1));
            boolean isStatic = m.group(2) != null;
            String paramList = m.group(5);
            int braceIndex = m.end() - 1;
            int bodyEnd = findMatchingBrace(body, braceIndex);
            if (bodyEnd < 0) {
                bodyEnd = body.length() - 1;
            }
            String methodBody = safeSubstring(body, braceIndex + 1, bodyEnd);
            int lines = (int) methodBody.lines().count();
            int instanceofCount = countOccurrences(methodBody, "instanceof");
            int switchCount = countWordOccurrences(methodBody, "switch");
            List<String> paramTypes = parseParamTypes(paramList);

            found.add(new FoundMethod(
                    new MethodModel(m.group(4), isPublic, isStatic, paramTypes, lines, instanceofCount, switchCount),
                    new int[] {m.start(), Math.max(bodyEnd, braceIndex)}));
            searchFrom = Math.max(bodyEnd + 1, m.end());
        }
        return found;
    }

    private List<String> parseParamTypes(String paramList) {
        if (paramList == null || paramList.isBlank()) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        for (String param : paramList.split(",")) {
            String trimmed = param.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length >= 2) {
                types.add(tokens[tokens.length - 2].replace("...", ""));
            } else {
                types.add(tokens[0]);
            }
        }
        return types;
    }

    // ---- field discovery -------------------------------------------------

    private record FieldStats(int count, int publicMutable, List<String> fieldTypes) {
    }

    private FieldStats findFields(String body) {
        Matcher m = FIELD_DECL.matcher(body);
        int count = 0;
        int publicMutable = 0;
        List<String> types = new ArrayList<>();
        while (m.find()) {
            boolean isPublic = "public".equals(m.group(1));
            boolean isFinal = m.group(3) != null;
            String type = m.group(4).trim();
            count++;
            types.add(type);
            if (isPublic && !isFinal) {
                publicMutable++;
            }
        }
        return new FieldStats(count, publicMutable, types);
    }

    private List<String> collectDependencies(List<String> fieldTypes, List<FoundMethod> methods) {
        List<String> all = new ArrayList<>(fieldTypes);
        for (FoundMethod fm : methods) {
            all.addAll(fm.model().parameterTypes());
        }
        List<String> deps = new ArrayList<>();
        for (String raw : all) {
            String base = raw.replaceAll("[\\[\\]<>].*", "").trim();
            if (base.isEmpty() || !Character.isUpperCase(base.charAt(0))) {
                continue;
            }
            if (COMMON_JDK_TYPES.contains(base)) {
                continue;
            }
            if (!deps.contains(base)) {
                deps.add(base);
            }
        }
        return deps;
    }

    // ---- text sanitisation -------------------------------------------------

    private String stripCommentsAndLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i + 1 < n) {
                    out.append("  ");
                    i += 2;
                } else {
                    i = n;
                }
            } else if (c == '"') {
                boolean textBlock = source.startsWith("\"\"\"", i);
                int skip = textBlock ? 3 : 1;
                out.append(" ".repeat(skip));
                i += skip;
                while (i < n) {
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        out.append("  ");
                        i += 2;
                        continue;
                    }
                    if (textBlock ? source.startsWith("\"\"\"", i) : source.charAt(i) == '"') {
                        out.append(" ".repeat(skip));
                        i += skip;
                        break;
                    }
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
            } else if (c == '\'') {
                out.append(' ');
                i++;
                while (i < n) {
                    if (source.charAt(i) == '\\' && i + 1 < n) {
                        out.append("  ");
                        i += 2;
                        continue;
                    }
                    boolean end = source.charAt(i) == '\'';
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                    if (end) {
                        break;
                    }
                }
            } else if (c == '@' && i + 1 < n && Character.isJavaIdentifierStart(source.charAt(i + 1))) {
                // Blank out an annotation's '@Name' so it can never be mis-read as part of a
                // method/field's return type -- without this, a lazy "type" group can swallow
                // backward across "@Override\n" and the modifiers that follow it, which was
                // silently reporting real public methods as non-public.
                out.append(' ');
                i++;
                while (i < n && (Character.isJavaIdentifierPart(source.charAt(i)) || source.charAt(i) == '.')) {
                    out.append(' ');
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private void checkBraceBalance(String cleaned, List<String> warnings) {
        int balance = 0;
        for (char c : cleaned.toCharArray()) {
            if (c == '{') {
                balance++;
            } else if (c == '}') {
                balance--;
                if (balance < 0) {
                    warnings.add("Found an unmatched closing '}'. Some structure may not have been parsed.");
                    balance = 0;
                }
            }
        }
        if (balance > 0) {
            warnings.add(balance + " unclosed '{' detected. Some types or methods may not have been parsed.");
        }
    }

    private int findMatchingBrace(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String maskNestedSpans(String body, List<RawType> allTypes, RawType self) {
        List<int[]> spans = new ArrayList<>();
        for (RawType other : allTypes) {
            if (other == self) {
                continue;
            }
            boolean directlyInside = other.declStart > self.braceIndex && other.declStart < self.bodyEnd;
            if (directlyInside) {
                int relStart = other.declStart - (self.braceIndex + 1);
                int relEnd = other.bodyEnd - (self.braceIndex + 1);
                if (relStart >= 0 && relEnd < body.length()) {
                    spans.add(new int[] {relStart, relEnd});
                }
            }
        }
        return maskSpans(body, spans);
    }

    private String maskSpans(String text, List<int[]> spans) {
        if (spans.isEmpty()) {
            return text;
        }
        char[] chars = text.toCharArray();
        for (int[] span : spans) {
            int start = Math.max(0, span[0]);
            int end = Math.min(chars.length - 1, span[1]);
            for (int i = start; i <= end; i++) {
                if (chars[i] != '\n') {
                    chars[i] = ' ';
                }
            }
        }
        return new String(chars);
    }

    private String safeSubstring(String text, int start, int end) {
        int s = Math.max(0, Math.min(start, text.length()));
        int e = Math.max(s, Math.min(end, text.length()));
        return text.substring(s, e);
    }

    private int countOccurrences(String text, String word) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) >= 0) {
            count++;
            idx += word.length();
        }
        return count;
    }

    private int countWordOccurrences(String text, String word) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private record RawType(String name, TypeKind kind, List<String> supertypes, int declStart, int braceIndex,
                            int bodyEnd) {
    }
}
