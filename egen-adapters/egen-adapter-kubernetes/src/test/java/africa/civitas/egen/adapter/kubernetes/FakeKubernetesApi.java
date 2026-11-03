package africa.civitas.egen.adapter.kubernetes;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** Faux serveur de l'API Kubernetes (niveau 2) : enregistre chaque requete et repond par une fonction. */
final class FakeKubernetesApi implements AutoCloseable {

    record Recorded(String method, String path, String rawQuery, String contentType, String authorization,
                    String body) {
    }

    record Reply(int status, String body) {
        static Reply ok(String body) {
            return new Reply(200, body);
        }

        static Reply notFound() {
            return new Reply(404, "{\"kind\":\"Status\",\"reason\":\"NotFound\"}");
        }
    }

    private final HttpServer server;
    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile Function<Recorded, Reply> responder = request -> Reply.ok("{}");

    FakeKubernetesApi() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Recorded recorded = new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Authorization"), body);
            requests.add(recorded);
            Reply reply = responder.apply(recorded);
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    URI address() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    void respondWith(Function<Recorded, Reply> responder) {
        this.responder = responder;
    }

    List<Recorded> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
