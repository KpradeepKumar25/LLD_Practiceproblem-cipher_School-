package lldpractice.web;

import com.sun.net.httpserver.HttpExchange;

/** A learner is just a name kept in a cookie; there is no login. {@link #of} defaults to
 * "guest" so every page works before anyone has typed a name. */
public final class Learner {

    private static final String COOKIE_NAME = "learner";
    private static final String DEFAULT_NAME = "guest";

    private Learner() {
    }

    public static String of(HttpExchange exchange) {
        String value = HttpUtil.cookie(exchange, COOKIE_NAME);
        return (value == null || value.isBlank()) ? DEFAULT_NAME : value;
    }

    public static void set(HttpExchange exchange, String name) {
        HttpUtil.setCookie(exchange, COOKIE_NAME, name);
    }
}
