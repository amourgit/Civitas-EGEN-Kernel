package africa.civitas.egen.adapter.consul;

import africa.civitas.egen.application.port.DiscoveryException;
import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.ResolvedInstance;
import africa.civitas.egen.application.port.ResolvedInstances;
import africa.civitas.egen.application.port.ServiceInstanceRegistration;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.ServiceId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation du {@link DiscoveryPort} contre l'API HTTP Consul reelle —
 * voir docs/architecture/07-ports-et-adapters.md, "Discovery Port + Consul
 * Adapter" pour le mapping complet et les pieges connus. Seul point du
 * Kernel a parler HTTP a Consul.
 */
public final class ConsulDiscoveryAdapter implements DiscoveryPort {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final URI baseUri;
    private final String consulToken; // nullable — voir docs/architecture/14-securite.md
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsulDiscoveryAdapter(URI baseUri, String consulToken) {
        this.baseUri = baseUri;
        this.consulToken = consulToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    @Override
    public void register(ServiceInstanceRegistration registration) {
        // PUT /v1/agent/service/register est idempotent par construction cote
        // Consul (meme ID -> remplace l'enregistrement existant, voir 07.2.2) :
        // le bloc Check est inclus dans le MEME appel plutot que deux appels
        // separes.
        ObjectNode body = mapper.createObjectNode();
        body.put("ID", registration.instanceId());
        body.put("Name", registration.serviceId().value());
        body.put("Address", registration.address());
        body.put("Port", registration.port());

        HealthSpec health = registration.health();
        ObjectNode check = body.putObject("Check");
        check.put("HTTP", "http://" + registration.address() + ":" + registration.port()
                + health.httpEndpoint());
        check.put("Interval", health.interval().toSeconds() + "s");
        check.put("Timeout", health.timeout().toSeconds() + "s");
        // DeregisterCriticalServiceAfter : nettoyage cote Consul si une instance
        // reste en echec durablement sans jamais etre deregistree explicitement
        // (filet de securite, complementaire — pas un substitut — a
        // l'arret gracieux explicite, voir docs/architecture/09-cycle-de-vie.md, §9.3).
        check.put("DeregisterCriticalServiceAfter", "5m");

        HttpResponse<String> response = send(putJson("/v1/agent/service/register", body));
        requireSuccess(response, "register(" + registration.serviceId() + "/" + registration.instanceId() + ")");
    }

    @Override
    public void deregister(ServiceId id, String instanceId) {
        HttpResponse<String> response =
                send(putEmpty("/v1/agent/service/deregister/" + instanceId));
        requireSuccessOrAlreadyGone(response, "deregister(" + id + "/" + instanceId + ")");
    }

    @Override
    public ResolvedInstances resolve(ServiceId id) {
        // ?passing=true est LA cle (voir 07.2.2) : Consul filtre deja
        // cote serveur les instances non saines, l'adapter n'a pas a
        // reimplementer ce filtrage.
        HttpResponse<String> response =
                send(getRequest("/v1/health/service/" + id.value() + "?passing=true"));
        if (response.statusCode() == 404) {
            return ResolvedInstances.empty();
        }
        requireSuccess(response, "resolve(" + id + ")");

        List<ResolvedInstance> instances = new ArrayList<>();
        for (JsonNode entry : parseJson(response)) {
            JsonNode service = entry.path("Service");
            String instanceId = textOrEmpty(service, "ID");
            String address = textOrEmpty(service, "Address");
            int port = service.path("Port").asInt(0);
            if (!instanceId.isEmpty() && !address.isEmpty() && port > 0) {
                instances.add(new ResolvedInstance(instanceId, address, port));
            }
        }
        return new ResolvedInstances(instances);
    }

    // ------------------------------------------------------------------
    // HTTP bas niveau
    // ------------------------------------------------------------------

    private HttpRequest.Builder baseRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(REQUEST_TIMEOUT);
        if (consulToken != null && !consulToken.isBlank()) {
            builder.header("X-Consul-Token", consulToken);
        }
        return builder;
    }

    private HttpRequest getRequest(String path) {
        return baseRequest(path).GET().build();
    }

    private HttpRequest putJson(String path, ObjectNode body) {
        return baseRequest(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(writeJson(body)))
                .build();
    }

    private HttpRequest putEmpty(String path) {
        return baseRequest(path).PUT(HttpRequest.BodyPublishers.noBody()).build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DiscoveryException("Appel Consul impossible (" + request.method() + " "
                    + request.uri() + ") : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DiscoveryException("Appel Consul interrompu (" + request.method() + " "
                    + request.uri() + ")", e);
        }
    }

    private void requireSuccess(HttpResponse<String> response, String context) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DiscoveryException("Consul a rejete " + context + " : HTTP "
                    + response.statusCode() + " — " + response.body());
        }
    }

    /** deregister() d'une instance deja absente n'est pas une erreur — idempotence (voir 04.3). */
    private void requireSuccessOrAlreadyGone(HttpResponse<String> response, String context) {
        if (response.statusCode() == 404) {
            return;
        }
        requireSuccess(response, context);
    }

    private JsonNode parseJson(HttpResponse<String> response) {
        try {
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new DiscoveryException("Reponse Consul illisible : " + e.getMessage(), e);
        }
    }

    private String writeJson(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new DiscoveryException("Serialisation JSON impossible : " + e.getMessage(), e);
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }
}
