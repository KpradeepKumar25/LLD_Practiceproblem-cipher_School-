package lldpractice.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

/** Small helpers over {@link HttpExchange} for the handful of things handlers need: reading a
 * form/query body, writing a response, and reading/setting the one cookie the app uses. No web
 * framework is pulled in since the surface area here is tiny. */
public final class HttpUtil {

    private HttpUtil() {
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    public static Map<String, String> parseForm(String body) {
        return parseQuery(body);
    }

    public static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String pair : raw.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            result.put(decode(key), decode(value));
        }
        return result;
    }

    public static Map<String, String> queryParams(HttpExchange exchange) {
        return parseQuery(exchange.getRequestURI().getRawQuery());
    }

    public static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String cookie(HttpExchange exchange, String name) {
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                int eq = trimmed.indexOf('=');
                if (eq > 0 && trimmed.substring(0, eq).equals(name)) {
                    return decode(trimmed.substring(eq + 1));
                }
            }
        }
        return null;
    }

    public static void setCookie(HttpExchange exchange, String name, String value) {
        exchange.getResponseHeaders().add("Set-Cookie",
                name + "=" + encode(value) + "; Path=/; Max-Age=" + (60 * 60 * 24 * 30));
    }

    public static void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    public static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    public static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.getResponseBody().close();
    }
}
