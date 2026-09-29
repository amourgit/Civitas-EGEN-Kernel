package africa.civitas.egen.adapter.consul;

import africa.civitas.egen.application.port.ConfigChangeHandler;
import africa.civitas.egen.application.port.ConfigSet;
import africa.civitas.egen.application.port.ConfigVersion;
import africa.civitas.egen.application.port.ConfigWatch;
import africa.civitas.egen.application.port.ConfigurationException;
import africa.civitas.egen.application.port.ConfigurationPort;
import africa.civitas.egen.application.port.ResolvedConfig;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation du {@link ConfigurationPort} contre l'API HTTP Consul KV
 * reelle — voir docs/architecture/07-ports-et-adapters.md, "Configuration
 * Port" (store cle/valeur simple, Consul KV deja present dans la stack
 * plutot qu'un systeme supplementaire, garde-fou n3,
 * docs/architecture/02-principes-fondamentaux.md).
 *
 * <p>Un {@code ConfigSet} entier est stocke comme UN SEUL blob JSON a la
 * cle {@code egen/config/<environment>/<serviceId>} — plus simple qu'une
 * cle par valeur, suffisant pour la V1 (resolve/publish/watch portent sur
 * un seul appel Consul).</p>
 */
public final class ConsulConfigurationAdapter implements ConfigurationPort {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    // Duree du long-polling Consul (blocking query) pour watch() — voir
    // docs/architecture/07-ports-et-adapters.md, "Watch catalogue".
    private static final Duration WATCH_POLL_TIMEOUT = Duration.ofMinutes(5);

    private final URI baseUri;
    private final String consulToken;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, AtomicBoolean> activeWatches = new ConcurrentHashMap<>();
    private final ExecutorService watchExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "egen-config-watch");
        t.setDaemon(true);
        return t;
    });

    public ConsulConfigurationAdapter(URI baseUri, String consulToken) {
        this.baseUri = baseUri;
        this.consulToken = consulToken;
        this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    }

    @Override
    public ResolvedConfig resolve(ServiceId id, TargetEnvironment environment) {
        HttpResponse<String> response = send(getRequest(keyPath(id, environment), null));
        if (response.statusCode() == 404) {
            return ResolvedConfig.empty();
        }
        requireSuccess(response, "resolve(" + id + ")");
        return toResolvedConfig(parseJson(response));
    }

    @Override
    public void publish(ServiceId id, TargetEnvironment environment, ConfigSet values) {
        String json = writeJson(values.values());
        HttpResponse<String> response = send(putRequest(keyPath(id, environment), json));
        requireSuccess(response, "publish(" + id + ")");
    }

    @Override
    public ConfigWatch watch(ServiceId id, TargetEnvironment environment, ConfigChangeHandler handler) {
        String handle = UUID.randomUUID().toString();
        AtomicBoolean active = new AtomicBoolean(true);
        activeWatches.put(handle, active);

        watchExecutor.submit(() -> pollLoop(id, environment, handler, active));
        return new ConfigWatch(handle);
    }

    @Override
    public void unwatch(ConfigWatch watch) {
        AtomicBoolean active = activeWatches.remove(watch.nativeHandle());
        if (active != null) {
            active.set(false);
        }
    }

    @Override
    public ConfigVersion currentVersion(ServiceId id, TargetEnvironment environment) {
        HttpResponse<String> response = send(getRequest(keyPath(id, environment), null));
        if (response.statusCode() == 404) {
            return new ConfigVersion(0);
        }
        requireSuccess(response, "currentVersion(" + id + ")");
        return new ConfigVersion(extractModifyIndex(parseJson(response)));
    }

    // ------------------------------------------------------------------
    // Watch — long-polling via blocking query Consul, sur un thread dedie.
    // ------------------------------------------------------------------

    private void pollLoop(ServiceId id, TargetEnvironment environment, ConfigChangeHandler handler,
                           AtomicBoolean active) {
        // Etablit d'abord l'index Consul courant SANS notifier le handler :
        // watch() doit notifier des CHANGEMENTS survenant APRES son appel,
        // jamais de l'etat deja present au moment ou l'on commence a
        // observer. Sans cette ligne de base, la toute premiere requete du
        // watch (index=0) se comporte cote Consul comme une lecture NON
        // bloquante et renvoie immediatement la valeur deja publiee avant
        // watch() — ce qui declenchait un onChange "fantome" portant cette
        // valeur, pas un vrai changement.
        long index;
        try {
            index = currentVersion(id, environment).index();
        } catch (RuntimeException e) {
            index = 0;
        }

        while (active.get()) {
            try {
                HttpResponse<String> response = send(getRequest(keyPath(id, environment), index));
                if (response.statusCode() == 404) {
                    Thread.sleep(Duration.ofSeconds(5).toMillis());
                    continue;
                }
                requireSuccess(response, "watch(" + id + ")");
                JsonNode entry = parseJson(response);
                long newIndex = extractModifyIndex(entry);
                if (newIndex != index) {
                    index = newIndex;
                    handler.onChange(toResolvedConfig(entry));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // Une erreur transitoire ne doit jamais arreter silencieusement
                // le watch — court delai puis nouvelle tentative.
                sleepQuietly(Duration.ofSeconds(5));
            }
        }
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------
    // Traduction JSON <-> ConfigSet
    // ------------------------------------------------------------------

    private ResolvedConfig toResolvedConfig(JsonNode consulKvEntry) {
        // Reponse Consul KV : un tableau a un element {"Value": "<base64>", "ModifyIndex": N, ...}.
        JsonNode entry = consulKvEntry.isArray() ? consulKvEntry.get(0) : consulKvEntry;
        String base64Value = entry.path("Value").asText("");
        Map<String, String> values = new LinkedHashMap<>();
        if (!base64Value.isEmpty()) {
            String json = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            try {
                mapper.readTree(json).fields().forEachRemaining(f -> values.put(f.getKey(), f.getValue().asText()));
            } catch (IOException e) {
                throw new ConfigurationException("Valeur de configuration illisible : " + e.getMessage(), e);
            }
        }
        return new ResolvedConfig(values, new ConfigVersion(entry.path("ModifyIndex").asLong(0)));
    }

    private static long extractModifyIndex(JsonNode consulKvEntry) {
        JsonNode entry = consulKvEntry.isArray() ? consulKvEntry.get(0) : consulKvEntry;
        return entry.path("ModifyIndex").asLong(0);
    }

    private String writeJson(Map<String, String> values) {
        try {
            return mapper.writeValueAsString(new HashMap<>(values));
        } catch (IOException e) {
            throw new ConfigurationException("Serialisation JSON impossible : " + e.getMessage(), e);
        }
    }

    private static String keyPath(ServiceId id, TargetEnvironment environment) {
        return "/v1/kv/egen/config/" + environment.name() + "/" + id.value();
    }

    // ------------------------------------------------------------------
    // HTTP bas niveau
    // ------------------------------------------------------------------

    private HttpRequest.Builder baseRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path));
        if (consulToken != null && !consulToken.isBlank()) {
            builder.header("X-Consul-Token", consulToken);
        }
        return builder;
    }

    private HttpRequest getRequest(String path, Long blockingIndex) {
        String query = blockingIndex == null ? ""
                : "?index=" + blockingIndex + "&wait=" + WATCH_POLL_TIMEOUT.toMinutes() + "m";
        Duration timeout = blockingIndex == null ? REQUEST_TIMEOUT
                : WATCH_POLL_TIMEOUT.plus(REQUEST_TIMEOUT);
        return baseRequest(path + query).timeout(timeout).GET().build();
    }

    private HttpRequest putRequest(String path, String body) {
        return baseRequest(path).timeout(REQUEST_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ConfigurationException("Appel Consul KV impossible (" + request.method() + " "
                    + request.uri() + ") : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConfigurationException("Appel Consul KV interrompu (" + request.method() + " "
                    + request.uri() + ")", e);
        }
    }

    private void requireSuccess(HttpResponse<String> response, String context) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ConfigurationException("Consul KV a rejete " + context + " : HTTP "
                    + response.statusCode() + " — " + response.body());
        }
    }

    private JsonNode parseJson(HttpResponse<String> response) {
        try {
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new ConfigurationException("Reponse Consul KV illisible : " + e.getMessage(), e);
        }
    }
}
