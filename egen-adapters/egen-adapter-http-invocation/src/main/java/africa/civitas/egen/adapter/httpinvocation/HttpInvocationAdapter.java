package africa.civitas.egen.adapter.httpinvocation;

import africa.civitas.egen.application.port.InvocationResult;
import africa.civitas.egen.application.port.ServiceInvocationPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Implementation du {@link ServiceInvocationPort} — voir
 * docs/architecture/07-ports-et-adapters.md, "Service Invocation Port".
 * Seul module de ce reacteur a connaitre {@link HttpClient} pour CE port
 * (garde-fou n6, docs/architecture/02-principes-fondamentaux.md) — les
 * autres adapters utilisent deja {@code HttpClient} chacun pour leur propre
 * port (Nomad, Consul, Vault), sans lien entre eux.
 */
public final class HttpInvocationAdapter implements ServiceInvocationPort {

    private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpInvocationAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public InvocationResult invoke(URI endpoint, String method, Map<String, String> payload, Duration timeout) {
        if (endpoint == null) {
            throw new IllegalArgumentException("ServiceInvocationPort.invoke: endpoint est obligatoire");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("ServiceInvocationPort.invoke: method est obligatoire");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("ServiceInvocationPort.invoke: timeout doit etre strictement positif");
        }
        String upperMethod = method.toUpperCase();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint).timeout(timeout);
        if (METHODS_WITH_BODY.contains(upperMethod)) {
            String jsonBody = writeJson(payload == null ? Map.of() : payload);
            requestBuilder.method(upperMethod, HttpRequest.BodyPublishers.ofString(jsonBody))
                    .header("Content-Type", "application/json");
        } else {
            requestBuilder.method(upperMethod, HttpRequest.BodyPublishers.noBody());
        }
        requestBuilder.header("Accept", "application/json");

        HttpResponse<String> response;
        try {
            response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Appel reseau echoue vers " + endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException("Appel interrompu vers " + endpoint, new IOException(e));
        }

        return new InvocationResult(response.statusCode(), readJson(response.body()));
    }

    private String writeJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            // Map<String,String> est toujours serialisable — ne devrait jamais arriver.
            throw new IllegalStateException("Echec inattendu de serialisation JSON du payload", e);
        }
    }

    /**
     * Deserialise une reponse JSON en {@code Map<String,String>} neutre
     * (voir docs/architecture/07-ports-et-adapters.md, "payload/body
     * restent Map&lt;String,String&gt;, jamais un type JSON concret") —
     * une valeur non scalaire (objet/tableau JSON imbrique) est reduite a
     * sa representation textuelle JSON plutot que de faire echouer tout
     * l'appel : EGEN reste utilisable meme face a une reponse plus
     * structuree que ce que ce port peut representer fidelement, au prix
     * d'une perte de structure assumee et documentee ici plutot que
     * silencieuse.
     */
    private Map<String, String> readJson(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() { });
            Map<String, String> flattened = new LinkedHashMap<>();
            // Une valeur JSON "null" est omise plutot que mise dans la map :
            // Map.copyOf (comme tout Map immuable du JDK) interdit les
            // valeurs nulles — omettre la cle est le choix le moins
            // surprenant pour l'appelant (une cle absente, jamais une
            // exception pour un "null" pourtant valide en JSON).
            raw.forEach((key, value) -> {
                String stringified = stringify(value);
                if (stringified != null) {
                    flattened.put(key, stringified);
                }
            });
            return Map.copyOf(flattened);
        } catch (IOException e) {
            // Reponse non-JSON ou non-objet (ex. texte brut, tableau racine) :
            // transportee telle quelle sous une cle unique plutot que perdue.
            return Map.of("raw", body);
        }
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            return String.valueOf(value);
        }
    }
}
