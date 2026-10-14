package africa.civitas.egen.sdk;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Requete vers un service cible. {@code traceparent} et {@code correlationId}
 * sont optionnels : generes par le SDK s'ils sont absents, propages tels
 * quels sinon (docs/architecture/12, "Propagation de contexte").
 */
public record EgenRequest(String method, String path, byte[] body, Map<String, String> headers,
                          String traceparent, String correlationId) {

    public EgenRequest {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method obligatoire");
        }
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("path doit commencer par '/' (recu : " + path + ")");
        }
        method = method.toUpperCase(java.util.Locale.ROOT);
        body = body == null ? null : body.clone();
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static EgenRequest get(String path) {
        return new EgenRequest("GET", path, null, null, null, null);
    }

    public static EgenRequest delete(String path) {
        return new EgenRequest("DELETE", path, null, null, null, null);
    }

    public static EgenRequest post(String path, byte[] body) {
        return new EgenRequest("POST", path, body, null, null, null);
    }

    public static EgenRequest post(String path, String body) {
        return post(path, body.getBytes(StandardCharsets.UTF_8));
    }

    public static EgenRequest put(String path, byte[] body) {
        return new EgenRequest("PUT", path, body, null, null, null);
    }

    public EgenRequest withHeader(String name, String value) {
        Map<String, String> merged = new LinkedHashMap<>(headers);
        merged.put(name, value);
        return new EgenRequest(method, path, body, merged, traceparent, correlationId);
    }

    public EgenRequest withTraceparent(String value) {
        return new EgenRequest(method, path, body, headers, value, correlationId);
    }

    public EgenRequest withCorrelationId(String value) {
        return new EgenRequest(method, path, body, headers, traceparent, value);
    }

    @Override
    public byte[] body() {
        return body == null ? null : body.clone();
    }
}
