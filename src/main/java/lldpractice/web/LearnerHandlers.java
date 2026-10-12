package lldpractice.web;

import java.io.IOException;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

/** Handles the "Your name" form in the nav bar: no auth, just a cookie. */
public final class LearnerHandlers {

    public void setLearner(HttpExchange exchange, Map<String, String> params) throws IOException {
        Map<String, String> form = HttpUtil.parseForm(HttpUtil.readBody(exchange));
        String name = form.getOrDefault("name", "").trim();
        if (!name.isEmpty()) {
            Learner.set(exchange, name);
        }
        String redirectTo = form.getOrDefault("redirectTo", "");
        HttpUtil.redirect(exchange, redirectTo.isBlank() ? "/problems" : redirectTo);
    }
}
