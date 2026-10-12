package lldpractice.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import lldpractice.domain.exceptions.InvalidTransitionException;
import lldpractice.domain.exceptions.NotFoundException;
import lldpractice.domain.exceptions.ValidationException;

/**
 * A minimal front-controller router: register routes with {@code :name} path parameters, and it
 * dispatches by method + path, translating the handful of domain exceptions used throughout the
 * app into the right HTTP status. Kept hand-rolled rather than pulling in a web framework, since
 * the whole app has under twenty routes.
 */
public final class Router implements HttpHandler {

    @FunctionalInterface
    public interface RouteHandler {
        void handle(HttpExchange exchange, Map<String, String> params) throws IOException;
    }

    private record Route(String method, Pattern pattern, List<String> paramNames, RouteHandler handler) {
    }

    private final List<Route> routes = new ArrayList<>();

    public Router get(String path, RouteHandler handler) {
        add("GET", path, handler);
        return this;
    }

    public Router post(String path, RouteHandler handler) {
        add("POST", path, handler);
        return this;
    }

    private void add(String method, String path, RouteHandler handler) {
        List<String> names = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            regex.append('/');
            if (segment.startsWith(":")) {
                names.add(segment.substring(1));
                regex.append("([^/]+)");
            } else {
                regex.append(Pattern.quote(segment));
            }
        }
        if (names.isEmpty() && regex.length() == 1) {
            regex.append("/?");
        }
        regex.append("/?$");
        routes.add(new Route(method, Pattern.compile(regex.toString()), names, handler));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        for (Route route : routes) {
            if (!route.method().equals(method)) {
                continue;
            }
            Matcher matcher = route.pattern().matcher(path);
            if (!matcher.matches()) {
                continue;
            }
            Map<String, String> params = new LinkedHashMap<>();
            for (int i = 0; i < route.paramNames().size(); i++) {
                params.put(route.paramNames().get(i), HttpUtil.decode(matcher.group(i + 1)));
            }
            dispatch(route, exchange, params);
            return;
        }
        HttpUtil.sendHtml(exchange, 404, Html.errorPage(404, "Not found", "No route for " + method + " " + path));
    }

    private void dispatch(Route route, HttpExchange exchange, Map<String, String> params) throws IOException {
        try {
            route.handler().handle(exchange, params);
        } catch (NotFoundException e) {
            HttpUtil.sendHtml(exchange, 404, Html.errorPage(404, "Not found", e.getMessage()));
        } catch (ValidationException e) {
            HttpUtil.sendHtml(exchange, 400, Html.errorPage(400, "Invalid request", e.getMessage()));
        } catch (InvalidTransitionException e) {
            HttpUtil.sendHtml(exchange, 409, Html.errorPage(409, "Conflicting state", e.getMessage()));
        } catch (Exception e) {
            HttpUtil.sendHtml(exchange, 500, Html.errorPage(500, "Internal error",
                    e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }
}
