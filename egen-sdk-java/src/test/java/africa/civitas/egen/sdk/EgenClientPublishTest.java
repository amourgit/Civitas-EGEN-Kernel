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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code publish} contre un VRAI serveur HTTP local qui joue le Kernel (docs/architecture/12, "Resilience de la
 * publication d'evenements"). Les memes scenarios, sous les memes noms, sont rejoues par le SDK Python.
 */
class EgenClientPublishTest {

    private static final String TYPE = "africa.civitas.news.article.created.v1";
    private static final String RECEIPT = "{\"id\":\"evt-1\",\"type\":\"" + TYPE + "\",\"egen_correlation_id\":\"c-1\"}";

    private record Seen(String method, String path, Map<String, String> headers, String body) {
    }

    private HttpServer kernel;
    private final List<Seen> seen = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Integer> statuses = new ArrayDeque<>();
    private volatile long delayMillis;
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final List<Long> sleeps = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void startKernel() throws IOException {
        kernel = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        kernel.createContext("/api/v1/events/publish", this::handle);
        kernel.start();
    }

    @AfterEach
    void stopKernel() {
        kernel.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new HashMap<>();
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
            status = statuses.isEmpty() ? 202 : statuses.poll();
        }
        byte[] body = (status == 202 ? RECEIPT : "refus-" + status).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void respond(Integer... codes) {
        synchronized (statuses) {
            statuses.addAll(List.of(codes));
        }
    }

    private static ResiliencyPolicy policy(int maxAttempts, int threshold, long timeoutMillis) {
        return new ResiliencyPolicy(timeoutMillis,
                new ResiliencyPolicy.Retry(maxAttempts, ResiliencyPolicy.Backoff.FIXED, 50),
                new ResiliencyPolicy.CircuitBreakerSpec(threshold, 60_000));
    }

    private EgenClient client(Consumer<EgenClient.Builder> customizer) {
        EgenClient.Builder builder = EgenClient.builder("http://127.0.0.1:" + kernel.getAddress().getPort())
                .eventSource("/news-service")
                .clockAndSleeper(clock::get, sleeps::add);
        customizer.accept(builder);
        return builder.build();
    }

    private EgenClient client() {
        return client(b -> { });
    }

    // ------------------------------------------------------------------ nominal

    @Test
    void publishRelaysAnEnvelopeAndReturnsTheReceipt() {
        PublishReceipt receipt = client(b -> b.tokenSupplier(() -> "jeton-123")).publish(TYPE, "{\"articleId\":\"42\"}");

        assertEquals(new PublishReceipt("evt-1", TYPE, "c-1"), receipt);
        assertEquals(1, seen.size());
        Seen request = seen.get(0);
        assertEquals("POST", request.method());
        assertEquals("/api/v1/events/publish", request.path());
        assertEquals("Bearer jeton-123", request.headers().get("authorization"));
        assertTrue(request.headers().get("content-type").startsWith("application/json"));
        assertTrue(request.headers().get("traceparent").matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
        Map<String, Object> envelope = Json.parseObject(request.body());
        assertEquals("1.0", envelope.get("specversion"));
        assertEquals(TYPE, envelope.get("type"));
        assertEquals("/news-service", envelope.get("source"));
        assertEquals(Map.of("articleId", "42"), envelope.get("data"));
        // L'egen_correlation_id du corps a la MEME valeur que l'en-tete (genere une seule fois).
        assertEquals(request.headers().get("egen-correlation-id"), envelope.get("egen_correlation_id"));
        assertTrue(request.headers().get("idempotency-key").matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void dataIsOpaqueAndNeverRewritten() {
        String data = "{\"titre\":\"Caf\u00e9 \\\"cr\u00e8me\\\"\",\"tags\":[1,2.5,null,true],\"nested\":{\"a\":[]}}";

        client().publish(TYPE, data);

        assertTrue(seen.get(0).body().contains("\"data\":" + data), seen.get(0).body());
    }

    @Test
    void aPerCallSourceOverridesTheClientsSource() {
        client().publish(TYPE, "{}", PublishOptions.none().withSource("/autre-service"));

        assertEquals("/autre-service", Json.parseObject(seen.get(0).body()).get("source"));
    }

    @Test
    void aCallerSuppliedContextAndIdempotencyKeyArePropagatedUnchanged() {
        String traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

        client().publish(TYPE, "{}", PublishOptions.none().withTraceparent(traceparent)
                .withCorrelationId("corr-123").withCausationId("cause-9").withIdempotencyKey("cle-fournie"));

        Seen request = seen.get(0);
        assertEquals(traceparent, request.headers().get("traceparent"));
        assertEquals("corr-123", request.headers().get("egen-correlation-id"));
        assertEquals("cle-fournie", request.headers().get("idempotency-key"));
        Map<String, Object> envelope = Json.parseObject(request.body());
        assertEquals("corr-123", envelope.get("egen_correlation_id"));
        assertEquals("cause-9", envelope.get("egen_causation_id"));
    }

    // ------------------------------------------------------------------ resilience (docs/architecture/12)

    @Test
    void theSameIdempotencyKeyAndContextAreSentOnEveryAttempt() {
        respond(503, 503, 202);

        PublishReceipt receipt = client(b -> b.publishPolicy(policy(3, 100, 5000))).publish(TYPE, "{}");

        assertEquals("evt-1", receipt.id());
        assertEquals(3, seen.size());
        assertEquals(List.of(50L, 50L), sleeps);
        assertEquals(1, seen.stream().map(s -> s.headers().get("idempotency-key")).distinct().count());
        assertEquals(1, seen.stream().map(s -> s.headers().get("egen-correlation-id")).distinct().count());
        assertEquals(1, seen.stream().map(s -> s.headers().get("traceparent")).distinct().count());
        assertEquals(1, seen.stream().map(Seen::body).distinct().count());
    }

    @Test
    void aDefinitiveResponseIsNeverRetried() {
        respond(403);

        PublishRejectedException e = assertThrows(PublishRejectedException.class,
                () -> client(b -> b.publishPolicy(policy(3, 100, 5000))).publish(TYPE, "{}"));

        assertEquals(403, e.status());
        assertEquals(1, e.attempts());
        assertEquals(1, seen.size());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void aPersistentServerFailureIsRejectedAfterAllAttempts() {
        respond(503, 503, 503);

        PublishRejectedException e = assertThrows(PublishRejectedException.class,
                () -> client(b -> b.publishPolicy(policy(3, 100, 5000))).publish(TYPE, "{}"));

        assertEquals(503, e.status());
        assertEquals(3, e.attempts());
        assertEquals(3, seen.size());
        assertTrue(e.getMessage().contains("refus-503"), e.getMessage());
    }

    @Test
    void byDefaultThereIsASingleAttempt() {
        respond(503, 202);

        assertThrows(PublishRejectedException.class, () -> client().publish(TYPE, "{}"));

        assertEquals(1, seen.size());
    }

    @Test
    void noResponseAtAllIsACallFailed() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        EgenClient client = EgenClient.builder("http://127.0.0.1:" + closedPort).eventSource("/s")
                .publishPolicy(policy(2, 100, 5000)).clockAndSleeper(clock::get, sleeps::add).build();

        CallFailedException e = assertThrows(CallFailedException.class, () -> client.publish(TYPE, "{}"));

        assertEquals("network", e.reason());
        assertEquals(2, e.attempts());
    }

    @Test
    void aTimeoutIsACallFailedWithReasonTimeout() {
        delayMillis = 400;

        CallFailedException e = assertThrows(CallFailedException.class,
                () -> client(b -> b.publishPolicy(policy(1, 100, 100))).publish(TYPE, "{}"));

        assertEquals("timeout", e.reason());
    }

    @Test
    void theDedicatedCircuitOpensAndRejectsWithoutAnyNetworkAccess() {
        respond(503, 503);
        EgenClient client = client(b -> b.publishPolicy(policy(1, 2, 5000)));

        assertThrows(PublishRejectedException.class, () -> client.publish(TYPE, "{}"));
        assertThrows(PublishRejectedException.class, () -> client.publish(TYPE, "{}"));
        assertThrows(CircuitOpenException.class, () -> client.publish(TYPE, "{}"));

        assertEquals(2, seen.size());
    }

    // ------------------------------------------------------------------ validation locale : rien n'est envoye

    @Test
    void aMissingTypeSourceOrInvalidDataIsRejectedBeforeAnyNetworkAccess() {
        EgenClient noSource = EgenClient.builder("http://127.0.0.1:" + kernel.getAddress().getPort()).build();

        assertThrows(IllegalArgumentException.class, () -> client().publish(" ", "{}"));
        assertThrows(IllegalArgumentException.class, () -> noSource.publish(TYPE, "{}"));
        assertThrows(IllegalArgumentException.class, () -> client().publish(TYPE, null));
        assertThrows(IllegalArgumentException.class, () -> client().publish(TYPE, "pas du json"));
        assertThrows(IllegalArgumentException.class, () -> client().publish(TYPE, "{} , \"type\":\"autre\""));

        assertTrue(seen.isEmpty());
    }

    @Test
    void anUnreadableReceiptIsAnExplicitError() {
        // Le Kernel repond 202 mais avec un corps qui n'est pas un accuse : jamais un succes silencieux.
        kernel.removeContext("/api/v1/events/publish");
        kernel.createContext("/api/v1/events/publish", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "pas du json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThrows(EgenException.class, () -> client().publish(TYPE, "{}"));
    }
}
