package lldpractice.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import lldpractice.service.PracticeService;

/** Wires every route to its handler and starts the {@link HttpServer}. The whole app is one
 * process, so this is also the entire "deployment" story for the MVP. */
public final class WebServer {

    private final HttpServer server;

    public WebServer(int port, PracticeService practiceService) throws IOException {
        ProblemHandlers problems = new ProblemHandlers(practiceService);
        AttemptHandlers attempts = new AttemptHandlers(practiceService);
        HistoryHandlers history = new HistoryHandlers(practiceService);
        ApiHandlers api = new ApiHandlers(practiceService);
        LearnerHandlers learnerHandlers = new LearnerHandlers();

        Router router = new Router()
                .get("/", (ex, p) -> HttpUtil.redirect(ex, "/problems"))
                .get("/problems", problems::list)
                .get("/problems/:id", problems::detail)
                .post("/problems/:id/attempts", problems::startAttempt)
                .get("/problems/:id/prefill/:kind", problems::prefill)
                .get("/attempts/:id", attempts::view)
                .post("/attempts/:id/draft", attempts::saveDraft)
                .post("/attempts/:id/submit", attempts::submit)
                .post("/attempts/:id/retry", attempts::retry)
                .get("/attempts/:id/try-again", attempts::tryAgain)
                .get("/attempts/:id/compare", attempts::compare)
                .get("/history", history::view)
                .post("/learner", learnerHandlers::setLearner)
                .get("/api/problems", api::problems)
                .get("/api/attempts/:id", api::attempt)
                .get("/api/history", api::history);

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }
}
