package africa.civitas.egen.sdk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le client contre de VRAIS serveurs HTTP locaux : un plan de controle simule et des instances cibles. */
class EgenClientTest {

    private record Seen(String method, String path, java.util.Map<String, String> headers, String body) {
    }

    /** Instance cible scriptee : statuts renvoyes dans l'ordre (200 par defaut), tout est enregistre. */
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
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers,
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
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        int port() {
            return server.getAddress().getPort();
        }

        Target respond(Integer... codes) {
            synchronized (statuses) {
                statuses.addAll(List.of(codes));
            }
            return this;
        }

        void stop() {
            server.stop(0);
        }
    }

    private HttpServer controlPlane;
    private final AtomicInteger discoverHits = new AtomicInteger();
    private volatile String discoverJson;
    private volatile int discoverStatus = 200;
    private final List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
    private final List<Target> targets = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final List<Long> sleeps = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void startControlPlane() throws IOException {
        controlPlane = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        controlPlane.createContext("/api/v1/discover/", exchange -> {
            discoverHits.incrementAndGet();
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            byte[] body = (discoverJson == null ? "{}" : discoverJson).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(discoverStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        controlPlane.start();
    }

    @AfterEach
    void stopServers() {
        controlPlane.stop(0);
        targets.forEach(Target::stop);
    }

    private Target target() throws IOException {
        Target target = new Target();
        targets.add(target);
        return target;
    }

    private static String instance(Target t, String id) {
        return "{\"instanceId\":\"" + id + "\",\"address\":\"127.0.0.1\",\"port\":" + t.port() + "}";
    }

    private void publish(String resiliencyJson, Target... instances) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < instances.length; i++) {
            parts.add(instance(instances[i], "inst-" + i));
        }
        discoverJson = "{\"serviceId\":\"news-service\",\"instances\":[" + String.join(",", parts) + "]"
                + (resiliencyJson == null ? "" : ",\"resiliency\":" + resiliencyJson) + "}";
    }

    private static String resiliency(int maxAttempts, int threshold, long timeoutMillis) {
        return "{\"timeoutMillis\":" + timeoutMillis + ",\"retry\":{\"maxAttempts\":" + maxAttempts
                + ",\"backoff\":\"fixed\",\"baseDelayMillis\":50},\"circuitBreaker\":{\"failureThreshold\":" + threshold
                + ",\"openDurationMillis\":60000}}";
    }

    private EgenClient client(Consumer<EgenClient.Builder> customizer) {
        EgenClient.Builder builder = EgenClient.builder("http://127.0.0.1:" + controlPlane.getAddress().getPort())
                .clockAndSleeper(clock::get, sleeps::add);
        customizer.accept(builder);
        return builder.build();
    }

    private EgenClient client() {
        return client(b -> { });
    }

    // ------------------------------------------------------------------ appel nominal

    @Test
    void callResolvesThenForwardsMethodPathAndBodyAndReturnsTheResponseAsIs() throws Exception {
        Target news = target();
        publish(null, news);

        EgenResponse response = client().call("news-service", EgenRequest.post("/internal/validate", "{\"id\":42}"));

        assertEquals(200, response.status());
        assertEquals("reponse-200", response.bodyAsString());
        assertTrue(response.isSuccess());
        assertEquals(1, news.seen.size());
        assertEquals("POST", news.seen.get(0).method());
        assertEquals("/internal/validate", news.seen.get(0).path());
        assertEquals("{\"id\":42}", news.seen.get(0).body());
    }

    @Test
    void generatesW3cTraceparentAndCorrelationIdWhenAbsent() throws Exception {
        Target news = target();
        publish(null, news);

        client().call("news-service", EgenRequest.get("/x"));

        Seen seen = news.seen.get(0);
        assertTrue(seen.headers().get("traceparent").matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"),
                seen.headers().get("traceparent"));
        assertTrue(seen.headers().get("egen-correlation-id").matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void propagatesTheCallersContextUnchangedAndKeepsItAcrossRetries() throws Exception {
        Target news = target().respond(503, 200);
        publish(resiliency(3, 100, 5000), news);
        String traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

        client().call("news-service", EgenRequest.get("/x").withTraceparent(traceparent)
                .withCorrelationId("corr-123").withHeader("X-Custom", "v"));

        assertEquals(2, news.seen.size());
        for (Seen seen : news.seen) {
            assertEquals(traceparent, seen.headers().get("traceparent"));
            assertEquals("corr-123", seen.headers().get("egen-correlation-id"));
            assertEquals("v", seen.headers().get("x-custom"));
        }
    }

    @Test
    void theControlPlaneTokenIsSentToTheControlPlaneOnlyNeverToTheTarget() throws Exception {
        Target news = target();
        publish(null, news);

        client(b -> b.tokenSupplier(() -> "secret-token")).call("news-service", EgenRequest.get("/x"));

        assertEquals(List.of("Bearer secret-token"), authorizations);
        assertFalse(news.seen.get(0).headers().containsKey("authorization"));
    }

    // ------------------------------------------------------------------ resilience

    @Test
    void aRetryTargetsTheNextInstanceNotTheFailingOne() throws Exception {
        Target broken = target().respond(503, 503, 503);
        Target healthy = target();
        publish(resiliency(3, 100, 5000), broken, healthy);

        EgenResponse response = client().call("news-service", EgenRequest.get("/x"));

        assertEquals(200, response.status());
        assertEquals(1, broken.seen.size(), "l'instance defaillante n'est pas retentee");
        assertEquals(1, healthy.seen.size());
        assertEquals(List.of(50L), sleeps);
    }

    @Test
    void successiveCallsRotateAcrossInstances() throws Exception {
        Target a = target();
        Target b = target();
        publish(null, a, b);
        EgenClient client = client();

        for (int i = 0; i < 4; i++) {
            client.call("news-service", EgenRequest.get("/x"));
        }

        assertEquals(2, a.seen.size());
        assertEquals(2, b.seen.size());
    }

    @Test
    void aDefinitiveClientErrorIsReturnedAsIsAndNeverRetried() throws Exception {
        Target news = target().respond(404, 200);
        publish(resiliency(3, 100, 5000), news);

        EgenResponse response = client().call("news-service", EgenRequest.get("/x"));

        assertEquals(404, response.status());
        assertEquals(1, news.seen.size());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void exhaustedRetriesOnA5xxReturnTheLastResponseInsteadOfThrowing() throws Exception {
        Target news = target().respond(503, 502, 500);
        publish(resiliency(3, 100, 5000), news);

        EgenResponse response = client().call("news-service", EgenRequest.get("/x"));

        assertEquals(500, response.status());
        assertEquals(3, news.seen.size());
        assertEquals(List.of(50L, 50L), sleeps);
    }

    @Test
    void theCircuitOpensAfterTheThresholdAndLaterCallsNeverReachTheNetwork() throws Exception {
        Target news = target().respond(503, 503);
        publish(resiliency(1, 2, 5000), news);
        EgenClient client = client();

        client.call("news-service", EgenRequest.get("/x"));
        client.call("news-service", EgenRequest.get("/x"));
        CircuitOpenException e = assertThrows(CircuitOpenException.class,
                () -> client.call("news-service", EgenRequest.get("/x")));

        assertEquals("news-service", e.service());
        assertEquals(2, news.seen.size(), "le troisieme appel est rejete sans acces reseau");
    }

    @Test
    void theCircuitClosesAgainAfterAnAdmittedProbeSucceeds() throws Exception {
        Target news = target().respond(503);
        publish(resiliency(1, 1, 5000), news);
        EgenClient client = client();
        client.call("news-service", EgenRequest.get("/x")); // ouvre le circuit (seuil 1)
        assertThrows(CircuitOpenException.class, () -> client.call("news-service", EgenRequest.get("/x")));

        clock.addAndGet(60_000); // openDuration ecoulee -> essai semi-ouvert admis
        EgenResponse probe = client.call("news-service", EgenRequest.get("/x"));

        assertEquals(200, probe.status());
        assertEquals(200, client.call("news-service", EgenRequest.get("/x")).status());
    }

    @Test
    void aSlowTargetEndsInATimeoutFailureAfterAllAttempts() throws Exception {
        Target news = target();
        news.delayMillis = 600;
        publish(resiliency(2, 100, 150), news);

        CallFailedException e = assertThrows(CallFailedException.class,
                () -> client().call("news-service", EgenRequest.get("/x")));

        assertEquals("timeout", e.reason());
        assertEquals(2, e.attempts());
    }

    @Test
    void anUnreachableInstanceEndsInANetworkFailure() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        discoverJson = "{\"serviceId\":\"news-service\",\"instances\":[{\"instanceId\":\"i\",\"address\":\"127.0.0.1\","
                + "\"port\":" + closedPort + "}],\"resiliency\":" + resiliency(2, 100, 2000) + "}";

        CallFailedException e = assertThrows(CallFailedException.class,
                () -> client().call("news-service", EgenRequest.get("/x")));

        assertEquals("network", e.reason());
        assertEquals(2, e.attempts());
    }

    // ------------------------------------------------------------------ resolution

    @Test
    void noInstanceFailsFastAndIsNeverCached() throws Exception {
        discoverJson = "{\"serviceId\":\"news-service\",\"instances\":[]}";
        EgenClient client = client();
        assertThrows(NoInstanceException.class, () -> client.call("news-service", EgenRequest.get("/x")));

        Target news = target();
        publish(null, news);

        assertEquals(200, client.call("news-service", EgenRequest.get("/x")).status());
        assertEquals(2, discoverHits.get(), "un resultat vide n'est pas garde en cache");
    }

    @Test
    void resolutionIsCachedUntilTheTtlExpires() throws Exception {
        Target news = target();
        publish(null, news);
        EgenClient client = client(b -> b.resolveCacheTtl(Duration.ofSeconds(5)));

        client.call("news-service", EgenRequest.get("/x"));
        client.call("news-service", EgenRequest.get("/x"));
        assertEquals(1, discoverHits.get());

        clock.addAndGet(5_000);
        client.call("news-service", EgenRequest.get("/x"));
        assertEquals(2, discoverHits.get());
    }

    @Test
    void aResolutionWithoutAResiliencyBlockUsesTheDefaults() throws Exception {
        Target news = target();
        publish(null, news);

        assertEquals(ResiliencyPolicy.defaults(), client().resolve("news-service").resiliency());
    }

    @Test
    void aControlPlaneErrorIsReportedExplicitly() {
        discoverStatus = 500;

        EgenException e = assertThrows(EgenException.class, () -> client().resolve("news-service"));

        assertTrue(e.getMessage().contains("HTTP 500"));
    }

    @Test
    void anUnreachableControlPlaneIsReportedExplicitly() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        EgenClient client = EgenClient.builder("http://127.0.0.1:" + closedPort).build();

        assertThrows(EgenException.class, () -> client.resolve("news-service"));
    }

    @Test
    void anUnreadableResolutionIsReportedExplicitly() {
        discoverJson = "ceci n'est pas du json";

        assertThrows(EgenException.class, () -> client().resolve("news-service"));
    }

    @Test
    void targetsAreAddressedInHttpsOnlyWhenAnSslContextIsConfigured() throws Exception {
        assertEquals("http", client().targetScheme());
        assertEquals("https", client(b -> {
            try {
                b.sslContext(javax.net.ssl.SSLContext.getDefault());
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }).targetScheme());
    }

    @Test
    void requestValidation() {
        assertThrows(IllegalArgumentException.class, () -> EgenRequest.get("sans-slash"));
        assertThrows(IllegalArgumentException.class, () -> EgenClient.builder(" "));
        assertNull(EgenRequest.get("/x").body());
    }
}
