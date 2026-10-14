package africa.civitas.egen.sidecar;

import africa.civitas.egen.sdk.CallFailedException;
import africa.civitas.egen.sdk.CircuitOpenException;
import africa.civitas.egen.sdk.EgenClient;
import africa.civitas.egen.sdk.EgenException;
import africa.civitas.egen.sdk.EgenRequest;
import africa.civitas.egen.sdk.EgenResponse;
import africa.civitas.egen.sdk.NoInstanceException;
import africa.civitas.egen.sdk.ResiliencyPolicy;
import africa.civitas.egen.sdk.ServiceInstance;
import africa.civitas.egen.sdk.ServiceResolution;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Sidecar de reference (docs/architecture/12, "Sidecar de reference").
 * N'a AUCUNE logique de resilience : tout passe par {@link EgenClient}.
 *
 * <pre>
 * GET /discover/{service}            resolution (instances + politique de la cible)
 * ANY /call/{service}/{chemin...}    appel avec resilience et propagation de contexte
 * GET /health                        200 si le processus repond
 * </pre>
 *
 * <p>N'ecoute JAMAIS que sur une adresse de boucle locale : il porte le jeton
 * de l'API de controle et n'a aucune authentification propre.</p>
 */
public final class SidecarServer {

    /** En-tetes qu'un proxy ne doit pas relayer (RFC 9110) + ceux que le client HTTP du JDK interdit. */
    private static final Set<String> HOP_BY_HOP = Set.of("connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length", "expect");

    private final EgenClient client;
    private final HttpServer server;

    public SidecarServer(EgenClient client, String host, int port) throws IOException {
        if (!InetAddress.getByName(host).isLoopbackAddress()) {
            throw new IllegalArgumentException("Le sidecar n'ecoute que sur une adresse de boucle locale (recu : "
                    + host + ") — jamais sur une interface publique");
        }
        this.client = client;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(host), port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.createContext("/health", this::health);
        this.server.createContext("/discover/", this::discover);
        this.server.createContext("/call/", this::call);
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

    // ------------------------------------------------------------------ routes

    private void health(HttpExchange exchange) throws IOException {
        reply(exchange, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8), Headers.of());
    }

    private void discover(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            reply(exchange, 405, "application/json", error("Methode non autorisee"), Headers.of("Allow", "GET"));
            return;
        }
        String service = decode(exchange.getRequestURI().getRawPath().substring("/discover/".length()));
        if (service.isBlank() || service.contains("/")) {
            reply(exchange, 400, "application/json", error("Nom de service attendu : /discover/{service}"), Headers.of());
            return;
        }
        try {
            reply(exchange, 200, "application/json", resolutionJson(client.resolve(service)), Headers.of());
        } catch (EgenException e) {
            reply(exchange, 502, "application/json", error(e.getMessage()), Headers.of());
        }
    }

    private void call(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getRawPath().substring("/call/".length());
        int slash = rawPath.indexOf('/');
        String service = decode(slash < 0 ? rawPath : rawPath.substring(0, slash));
        if (service.isBlank() || slash < 0) {
            reply(exchange, 400, "application/json", error("Attendu : /call/{service}/{chemin}"), Headers.of());
            return;
        }
        String query = exchange.getRequestURI().getRawQuery();
        String targetPath = rawPath.substring(slash) + (query == null ? "" : "?" + query);

        byte[] body = exchange.getRequestBody().readAllBytes();
        EgenRequest request = new EgenRequest(exchange.getRequestMethod(), targetPath,
                body.length == 0 ? null : body, forwardableHeaders(exchange.getRequestHeaders()),
                exchange.getRequestHeaders().getFirst(EgenClient.TRACEPARENT_HEADER),
                exchange.getRequestHeaders().getFirst(EgenClient.CORRELATION_HEADER));
        try {
            EgenResponse response = client.call(service, request);
            Headers out = new Headers();
            response.headers().forEach((name, values) -> {
                if (!HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT)) && !name.startsWith(":")) {
                    values.forEach(v -> out.add(name, v));
                }
            });
            exchange.getResponseHeaders().putAll(out);
            sendBody(exchange, response.status(), response.body());
        } catch (CircuitOpenException e) {
            reply(exchange, 503, "application/json", error(e.getMessage()), Headers.of("Egen-Circuit", "open"));
        } catch (NoInstanceException e) {
            reply(exchange, 503, "application/json", error(e.getMessage()), Headers.of("Egen-Resolution", "none"));
        } catch (CallFailedException e) {
            reply(exchange, e.reason().equals("timeout") ? 504 : 502, "application/json", error(e.getMessage()),
                    Headers.of("Egen-Failure", e.reason()));
        } catch (EgenException e) {
            reply(exchange, 502, "application/json", error(e.getMessage()), Headers.of());
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private static java.util.Map<String, String> forwardableHeaders(Headers incoming) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        incoming.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            // traceparent et Egen-Correlation-Id sont portes par EgenRequest (generes si absents).
            if (!HOP_BY_HOP.contains(lower) && !lower.equals("traceparent") && !lower.equals("egen-correlation-id")) {
                headers.put(name, String.join(", ", values));
            }
        });
        return headers;
    }

    private static String decode(String raw) {
        return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static void reply(HttpExchange exchange, int status, String contentType, byte[] body, Headers extra)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        extra.forEach((name, values) -> values.forEach(v -> exchange.getResponseHeaders().add(name, v)));
        sendBody(exchange, status, body);
    }

    private static void sendBody(HttpExchange exchange, int status, byte[] body) throws IOException {
        boolean noBody = exchange.getRequestMethod().equals("HEAD") || status == 204 || status == 304;
        exchange.sendResponseHeaders(status, noBody ? -1 : body.length == 0 ? -1 : body.length);
        if (!noBody && body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private static byte[] error(String message) {
        return ("{\"error\":" + quote(message) + "}").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] resolutionJson(ServiceResolution resolution) {
        StringBuilder json = new StringBuilder("{\"serviceId\":").append(quote(resolution.serviceId()))
                .append(",\"instances\":[");
        List<ServiceInstance> instances = resolution.instances();
        for (int i = 0; i < instances.size(); i++) {
            ServiceInstance instance = instances.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"instanceId\":").append(quote(instance.instanceId()))
                    .append(",\"address\":").append(quote(instance.address()))
                    .append(",\"port\":").append(instance.port()).append('}');
        }
        ResiliencyPolicy policy = resolution.resiliency();
        json.append("],\"resiliency\":{\"timeoutMillis\":").append(policy.timeoutMillis())
                .append(",\"retry\":{\"maxAttempts\":").append(policy.retry().maxAttempts())
                .append(",\"backoff\":").append(quote(policy.retry().backoff().name().toLowerCase(Locale.ROOT)))
                .append(",\"baseDelayMillis\":").append(policy.retry().baseDelayMillis())
                .append("},\"circuitBreaker\":{\"failureThreshold\":").append(policy.circuitBreaker().failureThreshold())
                .append(",\"openDurationMillis\":").append(policy.circuitBreaker().openDurationMillis())
                .append("}}}");
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
