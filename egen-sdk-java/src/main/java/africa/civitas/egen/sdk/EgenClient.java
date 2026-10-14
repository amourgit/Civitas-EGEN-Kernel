package africa.civitas.egen.sdk;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Client de la Communication Fabric (docs/architecture/12) : resout un service
 * aupres de l'API de controle EGEN, puis l'appelle en appliquant la politique
 * de resilience de la CIBLE (retry, timeout, circuit breaker) et en propageant
 * le contexte ({@code traceparent}, {@code Egen-Correlation-Id}).
 *
 * <p>Ce que ce client ne fait jamais : transformer, enrichir ou router selon le
 * contenu d'un corps, ni retenter une reponse definitive (4xx hors 408/429).</p>
 *
 * <p>Thread-safe ; une instance par processus suffit (les circuits sont par
 * service cible et par client).</p>
 */
public final class EgenClient {

    public static final String CORRELATION_HEADER = "Egen-Correlation-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";

    private record CachedResolution(ServiceResolution resolution, long expiresAtMillis) {
    }

    private final String controlPlaneUrl;
    private final Supplier<String> tokenSupplier;
    private final HttpClient http;
    private final long resolveCacheTtlMillis;
    private final LongSupplier clockMillis;
    private final Sleeper sleeper;
    private final Duration controlPlaneTimeout;
    private final String targetScheme;

    private final ConcurrentMap<String, CachedResolution> resolutions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> roundRobin = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private EgenClient(Builder b) {
        this.controlPlaneUrl = b.controlPlaneUrl.endsWith("/")
                ? b.controlPlaneUrl.substring(0, b.controlPlaneUrl.length() - 1) : b.controlPlaneUrl;
        this.tokenSupplier = b.tokenSupplier;
        this.resolveCacheTtlMillis = b.resolveCacheTtl.toMillis();
        this.clockMillis = b.clockMillis;
        this.sleeper = b.sleeper;
        this.controlPlaneTimeout = b.controlPlaneTimeout;
        // Avec un SSLContext (mTLS), les services cibles sont adresses en https ; sinon en http.
        this.targetScheme = b.sslContext != null ? "https" : "http";
        HttpClient.Builder http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(b.connectTimeout);
        if (b.sslContext != null) {
            http.sslContext(b.sslContext); // mTLS : transmis tel quel, jamais gere par le SDK
        }
        this.http = http.build();
    }

    public static Builder builder(String controlPlaneUrl) {
        return new Builder(controlPlaneUrl);
    }

    public static final class Builder {
        private final String controlPlaneUrl;
        private Supplier<String> tokenSupplier;
        private SSLContext sslContext;
        private Duration resolveCacheTtl = Duration.ofSeconds(5);
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration controlPlaneTimeout = Duration.ofSeconds(5);
        private LongSupplier clockMillis = System::currentTimeMillis;
        private Sleeper sleeper = Sleeper.threadSleep();

        private Builder(String controlPlaneUrl) {
            if (controlPlaneUrl == null || controlPlaneUrl.isBlank()) {
                throw new IllegalArgumentException("controlPlaneUrl obligatoire");
            }
            this.controlPlaneUrl = controlPlaneUrl;
        }

        /** Jeton envoye a l'API de controle uniquement — jamais aux services cibles. */
        public Builder tokenSupplier(Supplier<String> tokenSupplier) {
            this.tokenSupplier = tokenSupplier;
            return this;
        }

        /** mTLS : le contexte est transmis tel quel au client HTTP et les cibles sont alors adressees en https. */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder resolveCacheTtl(Duration ttl) {
            this.resolveCacheTtl = Objects.requireNonNull(ttl);
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = Objects.requireNonNull(timeout);
            return this;
        }

        /** Horloge et attente injectables : pour les tests uniquement. */
        public Builder clockAndSleeper(LongSupplier clockMillis, Sleeper sleeper) {
            this.clockMillis = Objects.requireNonNull(clockMillis);
            this.sleeper = Objects.requireNonNull(sleeper);
            return this;
        }

        public EgenClient build() {
            return new EgenClient(this);
        }
    }

    // ------------------------------------------------------------------ resolution

    /**
     * Instances saines et politique de la cible, gardees en cache brievement
     * (5 s par defaut) pour ne pas faire du Kernel un intermediaire de chaque
     * appel. Un resultat SANS instance n'est jamais mis en cache.
     */
    public ServiceResolution resolve(String service) {
        long now = clockMillis.getAsLong();
        CachedResolution cached = resolutions.get(service);
        if (cached != null && now < cached.expiresAtMillis()) {
            return cached.resolution();
        }
        ServiceResolution fresh = fetchResolution(service);
        if (!fresh.instances().isEmpty()) {
            resolutions.put(service, new CachedResolution(fresh, now + resolveCacheTtlMillis));
        } else {
            resolutions.remove(service);
        }
        return fresh;
    }

    private ServiceResolution fetchResolution(String service) {
        URI uri = URI.create(controlPlaneUrl + "/api/v1/discover/"
                + URLEncoder.encode(service, StandardCharsets.UTF_8).replace("+", "%20"));
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET().timeout(controlPlaneTimeout)
                .header("Accept", "application/json");
        if (tokenSupplier != null) {
            String token = tokenSupplier.get();
            if (token != null && !token.isBlank()) {
                request.header("Authorization", "Bearer " + token);
            }
        }
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new EgenException("API de controle EGEN injoignable (" + uri + ") : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EgenException("Resolution de " + service + " interrompue", e);
        }
        if (response.statusCode() != 200) {
            throw new EgenException("API de controle EGEN : HTTP " + response.statusCode()
                    + " pour la resolution de " + service);
        }
        return parseResolution(service, response.body());
    }

    @SuppressWarnings("unchecked")
    static ServiceResolution parseResolution(String service, String json) {
        Map<String, Object> root;
        try {
            root = Json.parseObject(json);
        } catch (IllegalArgumentException e) {
            throw new EgenException("Reponse de resolution illisible pour " + service + " : " + e.getMessage(), e);
        }
        List<ServiceInstance> instances = new ArrayList<>();
        if (root.get("instances") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> instance = (Map<String, Object>) m;
                    instances.add(new ServiceInstance(
                            String.valueOf(instance.get("instanceId")),
                            String.valueOf(instance.get("address")),
                            ((Number) instance.get("port")).intValue()));
                }
            }
        }
        return new ServiceResolution(String.valueOf(root.getOrDefault("serviceId", service)), instances,
                ResiliencyPolicy.fromJson(root.get("resiliency")));
    }

    // ------------------------------------------------------------------ appel

    /**
     * Appelle {@code service} avec la politique de resilience de la cible.
     *
     * @return la reponse du service, telle quelle — y compris un 4xx, ou un 5xx
     *         une fois les tentatives epuisees
     * @throws NoInstanceException si aucune instance saine n'est resolue
     * @throws CircuitOpenException si le circuit est ouvert (aucun acces reseau)
     * @throws CallFailedException si aucune tentative n'a obtenu de reponse
     */
    public EgenResponse call(String service, EgenRequest request) {
        ServiceResolution resolution = resolve(service);
        List<ServiceInstance> instances = resolution.instances();
        if (instances.isEmpty()) {
            throw new NoInstanceException(service);
        }
        ResiliencyPolicy policy = resolution.resiliency();
        CircuitBreaker breaker = breakers.compute(service, (key, existing) ->
                existing != null && existing.spec().equals(policy.circuitBreaker())
                        ? existing : new CircuitBreaker(policy.circuitBreaker(), clockMillis));

        String traceparent = request.traceparent() != null ? request.traceparent() : newTraceparent();
        String correlationId = request.correlationId() != null ? request.correlationId() : UUID.randomUUID().toString();
        int start = roundRobin.computeIfAbsent(service, k -> new AtomicInteger()).getAndIncrement();

        ResilienceExecutor.Result result;
        try {
            result = new ResilienceExecutor(policy.retry(), breaker, sleeper).run(attempt -> {
                // Chaque tentative vise l'instance SUIVANTE : retenter une instance defaillante n'a guere de sens.
                ServiceInstance target = instances.get(Math.floorMod(start + attempt, instances.size()));
                return send(target, request, policy, traceparent, correlationId);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EgenException("Appel de " + service + " interrompu", e);
        }
        if (result.rejected()) {
            throw new CircuitOpenException(service);
        }
        Outcome last = result.last();
        if (last.response() != null) {
            return last.response();
        }
        throw new CallFailedException(service, last.error(), result.attemptsMade());
    }

    private Outcome send(ServiceInstance target, EgenRequest request, ResiliencyPolicy policy,
                         String traceparent, String correlationId) {
        HttpRequest.BodyPublisher body = request.body() == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(request.body());
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create(targetScheme + "://" + target.address() + ":" + target.port() + request.path()))
                .timeout(Duration.ofMillis(policy.timeoutMillis()))
                .method(request.method(), body);
        request.headers().forEach(builder::header);
        builder.header(TRACEPARENT_HEADER, traceparent);
        builder.header(CORRELATION_HEADER, correlationId);
        try {
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return Outcome.ofResponse(new EgenResponse(response.statusCode(), response.body(),
                    response.headers().map()));
        } catch (HttpTimeoutException e) {
            return Outcome.ofError("timeout");
        } catch (ConnectException e) {
            return Outcome.ofError("network");
        } catch (IOException e) {
            return Outcome.ofError("network");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EgenException("Appel interrompu", e);
        }
    }

    String targetScheme() {
        return targetScheme;
    }

    /** W3C Trace Context : {@code 00-<trace-id 32 hex>-<parent-id 16 hex>-01}. */
    String newTraceparent() {
        byte[] traceId = new byte[16];
        byte[] spanId = new byte[8];
        random.nextBytes(traceId);
        random.nextBytes(spanId);
        HexFormat hex = HexFormat.of();
        return "00-" + hex.formatHex(traceId) + "-" + hex.formatHex(spanId) + "-01";
    }
}
