package africa.civitas.egen.sidecar;

import africa.civitas.egen.sdk.EgenClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le sidecar de bout en bout : application simulee -> sidecar -> (plan de controle simule, instances cibles). */
class SidecarServerTest {

    private record Seen(String method, String path, Map<String, String> headers, String body) {
    }

    private static final class Target {
        final HttpServer server;
        final List<Seen> seen = Collections.synchronizedList(new ArrayList<>());
        final Deque<Integer> statuses = new ArrayDeque<>();
        volatile long delayMillis;

        Target() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        private void handle(HttpExchange exchange) throws IOException {
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            String path = exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
            seen.add(new Seen(exchange.getRequestMethod(), path, headers,
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            int status;
            synchronized (statuses) {
                status = statuses.isEmpty() ? 200 : statuses.poll();
            }
            byte[] body = ("reponse-" + status).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Upstream", "oui");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        Target respond(Integer... codes) {
            synchronized (statuses) {
                statuses.addAll(List.of(codes));
            }
            return this;
        }

        int port() {
            return server.getAddress().getPort();
        }
    }

    private HttpServer controlPlane;
    private volatile String discoverJson;
    private volatile int discoverStatus = 200;
    private final List<Target> targets = new ArrayList<>();
    private SidecarServer sidecar;
    private final HttpClient app = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws IOException {
        controlPlane = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        controlPlane.createContext("/api/v1/discover/", exchange -> {
            byte[] body = (discoverJson == null ? "{}" : discoverJson).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(discoverStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        controlPlane.start();
        sidecar = new SidecarServer(
                EgenClient.builder("http://127.0.0.1:" + controlPlane.getAddress().getPort()).build(), "127.0.0.1", 0);
        sidecar.start();
    }

    @AfterEach
    void stop() {
        sidecar.stop();
        controlPlane.stop(0);
        targets.forEach(t -> t.server.stop(0));
    }

    private Target target() throws IOException {
        Target target = new Target();
        targets.add(target);
        return target;
    }

    private void publish(String resiliency, Target... instances) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < instances.length; i++) {
            parts.add("{\"instanceId\":\"inst-" + i + "\",\"address\":\"127.0.0.1\",\"port\":" + instances[i].port() + "}");
        }
        discoverJson = "{\"serviceId\":\"news-service\",\"instances\":[" + String.join(",", parts) + "]"
                + (resiliency == null ? "" : ",\"resiliency\":" + resiliency) + "}";
    }

    private static String resiliency(int maxAttempts, int threshold, long timeoutMillis) {
        return "{\"timeoutMillis\":" + timeoutMillis + ",\"retry\":{\"maxAttempts\":" + maxAttempts
                + ",\"backoff\":\"fixed\",\"baseDelayMillis\":10},\"circuitBreaker\":{\"failureThreshold\":" + threshold
                + ",\"openDurationMillis\":60000}}";
    }

    private HttpResponse<String> send(String method, String path, String body, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + sidecar.port() + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return app.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ------------------------------------------------------------------ routes de base

    @Test
    void healthAnswers200() throws Exception {
        HttpResponse<String> response = send("GET", "/health", null);

        assertEquals(200, response.statusCode());
        assertEquals("ok", response.body());
    }

    @Test
    void discoverReturnsTheInstancesAndTheTargetsPolicy() throws Exception {
        Target news = target();
        publish(resiliency(3, 5, 10000), news);

        HttpResponse<String> response = send("GET", "/discover/news-service", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"serviceId\":\"news-service\""), response.body());
        assertTrue(response.body().contains("\"port\":" + news.port()), response.body());
        assertTrue(response.body().contains("\"maxAttempts\":3"), response.body());
        assertTrue(response.body().contains("\"timeoutMillis\":10000"), response.body());
    }

    @Test
    void discoverRejectsBadRequestsAndReportsAControlPlaneFailureAs502() throws Exception {
        assertEquals(405, send("POST", "/discover/news-service", "").statusCode());
        assertEquals(400, send("GET", "/discover/", null).statusCode());

        discoverStatus = 500;
        assertEquals(502, send("GET", "/discover/news-service", null).statusCode());
    }

    // ------------------------------------------------------------------ call

    @Test
    void callForwardsMethodPathQueryBodyAndHeadersAndReturnsTheResponseAsIs() throws Exception {
        Target news = target();
        publish(null, news);

        HttpResponse<String> response = send("POST", "/call/news-service/internal/validate?draft=true&x=%C3%A9",
                "{\"id\":42}", "Content-Type", "application/json", "X-Custom", "v");

        assertEquals(200, response.statusCode());
        assertEquals("reponse-200", response.body());
        assertEquals("oui", response.headers().firstValue("X-Upstream").orElse(null));
        Seen seen = news.seen.get(0);
        assertEquals("POST", seen.method());
        assertEquals("/internal/validate?draft=true&x=%C3%A9", seen.path());
        assertEquals("{\"id\":42}", seen.body());
        assertEquals("application/json", seen.headers().get("content-type"));
        assertEquals("v", seen.headers().get("x-custom"));
    }

    @Test
    void callGeneratesTheContextWhenAbsentAndPropagatesItWhenPresent() throws Exception {
        Target news = target();
        publish(null, news);

        send("GET", "/call/news-service/a", null);
        send("GET", "/call/news-service/b", null, "traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "Egen-Correlation-Id", "corr-9");

        assertTrue(news.seen.get(0).headers().get("traceparent").matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
        assertTrue(news.seen.get(0).headers().containsKey("egen-correlation-id"));
        assertEquals("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01", news.seen.get(1).headers().get("traceparent"));
        assertEquals("corr-9", news.seen.get(1).headers().get("egen-correlation-id"));
    }

    @Test
    void callPassesThroughADefinitive4xxAndALastFailing5xxAfterTheRetries() throws Exception {
        Target notFound = target().respond(404, 200);
        publish(resiliency(3, 100, 5000), notFound);
        assertEquals(404, send("GET", "/call/news-service/x", null).statusCode());
        assertEquals(1, notFound.seen.size());

        Target failing = target().respond(503, 503, 503);
        publish(resiliency(3, 100, 5000), failing);
        // Nouveau client de sidecar pour une resolution fraiche (le cache de 5 s garde la precedente).
        SidecarServer fresh = new SidecarServer(
                EgenClient.builder("http://127.0.0.1:" + controlPlane.getAddress().getPort()).build(), "127.0.0.1", 0);
        fresh.start();
        try {
            HttpResponse<String> response = app.send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + fresh.port() + "/call/news-service/x")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, response.statusCode());
            assertEquals(3, failing.seen.size());
            assertFalse(response.headers().firstValue("Egen-Circuit").isPresent(), "ce 503 vient de la cible, pas du circuit");
        } finally {
            fresh.stop();
        }
    }

    @Test
    void anOpenCircuitAnswers503WithTheCircuitHeaderAndNeverReachesTheTarget() throws Exception {
        Target news = target().respond(503, 503);
        publish(resiliency(1, 2, 5000), news);

        send("GET", "/call/news-service/x", null);
        send("GET", "/call/news-service/x", null);
        HttpResponse<String> rejected = send("GET", "/call/news-service/x", null);

        assertEquals(503, rejected.statusCode());
        assertEquals("open", rejected.headers().firstValue("Egen-Circuit").orElse(null));
        assertEquals(2, news.seen.size());
    }

    @Test
    void noInstanceAnswers503WithTheResolutionHeader() throws Exception {
        discoverJson = "{\"serviceId\":\"news-service\",\"instances\":[]}";

        HttpResponse<String> response = send("GET", "/call/news-service/x", null);

        assertEquals(503, response.statusCode());
        assertEquals("none", response.headers().firstValue("Egen-Resolution").orElse(null));
    }

    @Test
    void aSlowTargetAnswers504AndAnUnreachableOneAnswers502() throws Exception {
        Target slow = target();
        slow.delayMillis = 500;
        publish(resiliency(1, 100, 100), slow);
        HttpResponse<String> timeout = send("GET", "/call/news-service/x", null);
        assertEquals(504, timeout.statusCode());
        assertEquals("timeout", timeout.headers().firstValue("Egen-Failure").orElse(null));

        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        discoverJson = "{\"serviceId\":\"other-service\",\"instances\":[{\"instanceId\":\"i\",\"address\":\"127.0.0.1\","
                + "\"port\":" + closedPort + "}],\"resiliency\":" + resiliency(1, 100, 1000) + "}";
        HttpResponse<String> network = send("GET", "/call/other-service/x", null);
        assertEquals(502, network.statusCode());
        assertEquals("network", network.headers().firstValue("Egen-Failure").orElse(null));
    }

    @Test
    void callWithoutAPathIsABadRequest() throws Exception {
        assertEquals(400, send("GET", "/call/news-service", null).statusCode());
        assertEquals(400, send("GET", "/call/", null).statusCode());
    }

    // ------------------------------------------------------------------ securite

    @Test
    void theSidecarRefusesToListenOnAnythingButTheLoopback() {
        EgenClient client = EgenClient.builder("http://127.0.0.1:1").build();

        assertThrows(IllegalArgumentException.class, () -> new SidecarServer(client, "0.0.0.0", 0));
        assertThrows(IllegalArgumentException.class, () -> new SidecarServer(client, "192.168.1.10", 0));
    }
}
