package africa.civitas.egen.adapter.httpinvocation;

import africa.civitas.egen.application.port.InvocationResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exerce {@link HttpInvocationAdapter} contre un vrai serveur HTTP (le
 * {@link HttpServer} du JDK, embarque dans le test) plutot que des mocks —
 * aucun produit externe specifique a conteneuriser ici, contrairement aux
 * autres adapters (Nomad, Consul, NATS, Vault) qui en ont un (voir
 * docs/architecture/17-strategie-de-tests.md, niveau 3).
 */
class HttpInvocationAdapterIT {

    private HttpServer server;
    private HttpInvocationAdapter adapter;
    private URI baseUri;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        baseUri = URI.create("http://localhost:" + server.getAddress().getPort());
        adapter = new HttpInvocationAdapter();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void postsAJsonPayloadAndParsesAJsonResponse() throws IOException {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedMethod = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();
        server.createContext("/internal/validate", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"status\":\"ok\",\"articleId\":\"42\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        InvocationResult result = adapter.invoke(baseUri.resolve("/internal/validate"), "POST",
                Map.of("articleId", "42"), Duration.ofSeconds(5));

        assertEquals("POST", receivedMethod.get());
        assertEquals("application/json", receivedContentType.get());
        assertTrue(receivedBody.get().contains("\"articleId\":\"42\""));

        assertEquals(200, result.statusCode());
        assertTrue(result.isSuccess());
        assertEquals("ok", result.body().get("status"));
        assertEquals("42", result.body().get("articleId"));
    }

    @Test
    void aGetRequestSendsNoBody() throws IOException {
        AtomicReference<String> receivedMethod = new AtomicReference<>();
        AtomicReference<Long> contentLength = new AtomicReference<>();
        server.createContext("/internal/status", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length") == null ? 0L
                    : Long.parseLong(exchange.getRequestHeaders().getFirst("Content-Length")));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        InvocationResult result = adapter.invoke(baseUri.resolve("/internal/status"), "GET",
                Map.of(), Duration.ofSeconds(5));

        assertEquals("GET", receivedMethod.get());
        assertEquals(0L, contentLength.get());
        assertEquals(204, result.statusCode());
        assertTrue(result.body().isEmpty());
    }

    @Test
    void aServerErrorStatusIsAValidResultNeverAnException() throws IOException {
        server.createContext("/internal/boom", exchange -> {
            byte[] response = "{\"error\":\"database unreachable\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        InvocationResult result = adapter.invoke(baseUri.resolve("/internal/boom"), "POST",
                Map.of(), Duration.ofSeconds(5));

        assertEquals(503, result.statusCode());
        assertFalse(result.isSuccess());
        assertEquals("database unreachable", result.body().get("error"));
    }

    @Test
    void aNonJsonResponseBodyIsCarriedUnderARawKeyRatherThanLost() throws IOException {
        server.createContext("/internal/legacy", exchange -> {
            byte[] response = "plain text, not json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        InvocationResult result = adapter.invoke(baseUri.resolve("/internal/legacy"), "GET",
                Map.of(), Duration.ofSeconds(5));

        assertEquals("plain text, not json", result.body().get("raw"));
    }

    @Test
    void aJsonNullValueIsOmittedRatherThanCrashingOnAnImmutableMap() throws IOException {
        server.createContext("/internal/nullable", exchange -> {
            byte[] response = "{\"present\":\"yes\",\"absent\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        InvocationResult result = adapter.invoke(baseUri.resolve("/internal/nullable"), "GET",
                Map.of(), Duration.ofSeconds(5));

        assertEquals("yes", result.body().get("present"));
        assertFalse(result.body().containsKey("absent"));
    }

    @Test
    void aConnectionFailureIsReportedAsAnUncheckedIOException() {
        URI unreachable = URI.create("http://localhost:1"); // port reserve, jamais une connexion acceptee
        assertThrows(java.io.UncheckedIOException.class,
                () -> adapter.invoke(unreachable, "GET", Map.of(), Duration.ofSeconds(2)));
    }

    @Test
    void rejectsANullEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.invoke(null, "GET", Map.of(), Duration.ofSeconds(1)));
    }

    @Test
    void rejectsAZeroOrNegativeTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> adapter.invoke(baseUri, "GET", Map.of(), Duration.ZERO));
    }
}
