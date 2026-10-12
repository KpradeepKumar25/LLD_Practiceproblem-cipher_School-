package lldpractice.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free JSON reader/writer. The platform avoids a JSON library on purpose
 * (no Spring, keep the dependency footprint at zero) but still needs JSON for the web API and
 * for talking to the LLM. Only the subset needed here is supported: objects, arrays, strings,
 * numbers, booleans and null.
 */
public final class Json {

    private Json() {
    }

    // ---- writing -------------------------------------------------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(s, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(String.valueOf(entry.getKey()), sb);
                sb.append(':');
                writeValue(entry.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(item, sb);
            }
            sb.append(']');
        } else {
            writeString(value.toString(), sb);
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    public static Map<String, Object> object(Object... keyValuePairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }

    // ---- reading -------------------------------------------------

    /** Parses a JSON document into nested Map/List/String/Double/Boolean/null. */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        return value;
    }

    /**
     * Finds and extracts the first balanced top-level JSON object in free-form text, tolerating
     * markdown code fences or stray prose around it. Returns {@code null} if none is found.
     * Used to recover structured output from an LLM that did not follow the "JSON only" instruction.
     */
    public static String extractFirstObject(String raw) {
        int start = raw.indexOf('{');
        while (start >= 0) {
            int depth = 0;
            boolean inString = false;
            boolean escape = false;
            for (int i = start; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (inString) {
                    if (escape) {
                        escape = false;
                    } else if (c == '\\') {
                        escape = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return raw.substring(start, i + 1);
                    }
                }
            }
            start = raw.indexOf('{', start + 1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new IllegalArgumentException("Expected a JSON object but got: " + value);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        throw new IllegalArgumentException("Expected a JSON array but got: " + value);
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= text.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    pos++;
                } else if (next == '}') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Malformed JSON object near position " + pos);
                }
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char next = peek();
                if (next == ',') {
                    pos++;
                } else if (next == ']') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Malformed JSON array near position " + pos);
                }
            }
            return list;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = text.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Malformed boolean near position " + pos);
        }

        Object parseNull() {
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Malformed null near position " + pos);
        }

        Double parseNumber() {
            int start = pos;
            while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("Malformed number near position " + pos);
            }
            return Double.parseDouble(text.substring(start, pos));
        }

        char peek() {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            return text.charAt(pos);
        }

        void expect(char c) {
            if (pos >= text.length() || text.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' near position " + pos);
            }
            pos++;
        }
    }
}
