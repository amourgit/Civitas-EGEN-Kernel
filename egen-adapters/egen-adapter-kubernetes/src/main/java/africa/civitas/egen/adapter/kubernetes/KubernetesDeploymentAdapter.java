package africa.civitas.egen.adapter.kubernetes;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentException;
import africa.civitas.egen.application.port.DeploymentHandle;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation du {@link DeploymentPort} contre l'API HTTP Kubernetes — contrat normatif :
 * docs/architecture/07-ports-et-adapters.md, « Kubernetes Adapter ». Second adapter de
 * deploiement (Phase 6, docs/architecture/19-feuille-de-route.md), ajoute sans modifier
 * {@code egen-domain} ni {@code egen-application} (garde-fou n6,
 * docs/architecture/02-principes-fondamentaux.md).
 *
 * <p>Un {@code ServiceManifest} EGEN devient <b>un</b> {@code Deployment} {@code apps/v1} avec
 * <b>un</b> conteneur, tous deux nommes comme le service. L'adapter ne planifie rien et ne
 * reimplemente aucun rolling update : Kubernetes le fait (garde-fou n3). Comme l'adapter Nomad,
 * il parle HTTP avec le client du JDK et Jackson, sans SDK tiers.</p>
 */
public final class KubernetesDeploymentAdapter implements DeploymentPort {

    static final String FIELD_MANAGER = "egen-kernel";
    static final String LABEL_SERVICE_ID = "egen.civitas.africa/service-id";
    static final String LABEL_MANAGED_BY = "app.kubernetes.io/managed-by";
    static final String MANAGED_BY_VALUE = "egen-kernel";
    static final String PORT_NAME = "http";
    static final String PORT_ENV = "EGEN_PORT_http";
    static final String RESTARTED_AT_ANNOTATION = "kubectl.kubernetes.io/restartedAt";
    static final String REVISION_ANNOTATION = "deployment.kubernetes.io/revision";
    static final String POD_TEMPLATE_HASH_LABEL = "pod-template-hash";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String APPLY_PATCH = "application/apply-patch+yaml";
    private static final String MERGE_PATCH = "application/merge-patch+json";
    private static final String JSON_PATCH = "application/json-patch+json";

    private final KubernetesConfig config;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public KubernetesDeploymentAdapter(KubernetesConfig config) {
        this.config = config;
        String address = config.address().toString();
        this.baseUrl = address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT);
        if (config.caCertPath() != null) {
            builder.sslContext(sslContextTrusting(config));
        }
        this.httpClient = builder.build();
    }

    // ------------------------------------------------------------------
    // DeploymentPort
    // ------------------------------------------------------------------

    @Override
    public DeploymentHandle create(ServiceId id, DeploymentSpec spec) {
        applyDeployment(id, spec);
        return new DeploymentHandle(id, id.value());
    }

    @Override
    public void update(ServiceId id, DeploymentSpec newSpec) {
        // Meme appel que create() : server-side apply = upsert. Kubernetes realise lui-meme le
        // rolling update quand le modele de pod change (07, « Kubernetes Adapter »).
        applyDeployment(id, newSpec);
    }

    @Override
    public void scale(ServiceId id, int replicas) {
        HttpResponse<String> response = patchScale(id, replicas);
        requireSuccess(response, "scale(" + id + ")");
    }

    /** PATCH de la sous-ressource {@code scale} : le point d'acces dedie au nombre de replicas. */
    private HttpResponse<String> patchScale(ServiceId id, int replicas) {
        ObjectNode root = mapper.createObjectNode();
        root.putObject("spec").put("replicas", replicas);
        return send(patch(deploymentPath(id) + "/scale", "", MERGE_PATCH, root));
    }

    @Override
    public void restart(ServiceId id) {
        // Changer le modele de pod declenche un rolling update sans changer l'image.
        ObjectNode root = mapper.createObjectNode();
        root.putObject("spec").putObject("template").putObject("metadata").putObject("annotations")
                .put(RESTARTED_AT_ANNOTATION, Instant.now().toString());
        HttpResponse<String> response = send(patch(deploymentPath(id), "", MERGE_PATCH, root));
        requireSuccess(response, "restart(" + id + ")");
    }

    @Override
    public void stop(ServiceId id) {
        // Kubernetes n'a pas d'« arret qui garde l'historique » : le Deployment reste, a 0 replica.
        requireSuccessOrAlreadyGone(patchScale(id, 0), "stop(" + id + ")");
    }

    @Override
    public void remove(ServiceId id) {
        HttpResponse<String> response = send(baseRequest(deploymentPath(id) + "?propagationPolicy=Background")
                .DELETE().build());
        requireSuccessOrAlreadyGone(response, "remove(" + id + ")");
    }

    @Override
    public DeploymentObservation getStatus(ServiceId id) {
        HttpResponse<String> deployment = send(getRequest(deploymentPath(id)));
        if (deployment.statusCode() == 404) {
            return DeploymentObservation.empty();
        }
        requireSuccess(deployment, "getStatus(" + id + ")");
        int desired = parseJson(deployment).path("spec").path("replicas").asInt(0);

        int running = 0;
        int healthy = 0;
        int failed = 0;
        for (JsonNode pod : fetchPods(id)) {
            if (isTerminating(pod)) {
                continue;
            }
            String phase = textOrEmpty(pod.path("status"), "phase");
            if ("Running".equals(phase)) {
                running++;
                if (isReady(pod)) {
                    healthy++;
                }
            } else if ("Failed".equals(phase) || "Unknown".equals(phase)) {
                failed++;
            }
        }
        return new DeploymentObservation(desired, running, healthy, failed, Instant.now());
    }

    @Override
    public List<AllocationInfo> listAllocations(ServiceId id) {
        List<AllocationInfo> result = new ArrayList<>();
        for (JsonNode pod : fetchPods(id)) {
            if (isTerminating(pod)) {
                continue;
            }
            String name = textOrEmpty(pod.path("metadata"), "name");
            if (name.isEmpty()) {
                continue;
            }
            String status = allocationStatus(textOrEmpty(pod.path("status"), "phase"));
            String podIp = textOrEmpty(pod.path("status"), "podIP");
            boolean joinable = "running".equals(status) && !podIp.isEmpty();
            result.add(new AllocationInfo(name, status, podStart(pod),
                    joinable ? podIp : "", joinable ? config.containerPort() : 0));
        }
        return List.copyOf(result);
    }

    @Override
    public void rollback(ServiceId id, ServiceVersion targetVersion) {
        JsonNode template = findTemplateForVersion(id, targetVersion)
                .orElseThrow(() -> new DeploymentException(
                        "Aucune version Kubernetes de \"" + id + "\" ne correspond a l'image taguee \""
                                + targetVersion + "\" — impossible de revenir en arriere"));

        // Comme `kubectl rollout undo` : le modele du ReplicaSet, sans l'etiquette pod-template-hash.
        ArrayNode patch = mapper.createArrayNode();
        ObjectNode operation = patch.addObject();
        operation.put("op", "replace");
        operation.put("path", "/spec/template");
        operation.set("value", template);
        HttpResponse<String> response = send(baseRequest(deploymentPath(id))
                .header("Content-Type", JSON_PATCH)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(writeJson(patch)))
                .build());
        requireSuccess(response, "rollback(" + id + ", " + targetVersion + ")");
    }

    // ------------------------------------------------------------------
    // Traduction DeploymentSpec -> Deployment Kubernetes
    // ------------------------------------------------------------------

    private void applyDeployment(ServiceId id, DeploymentSpec spec) {
        ObjectNode deployment = buildDeployment(id, spec);
        String query = "?fieldManager=" + FIELD_MANAGER + "&force=true";
        HttpResponse<String> response = send(patch(deploymentPath(id), query, APPLY_PATCH, deployment));
        requireSuccess(response, "create/update(" + id + ")");
    }

    ObjectNode buildDeployment(ServiceId id, DeploymentSpec spec) {
        // Valide avant tout appel reseau.
        String cpu = KubernetesResourceQuantities.cpu(spec.cpu());
        String memory = KubernetesResourceQuantities.memory(spec.memory());

        ObjectNode root = mapper.createObjectNode();
        root.put("apiVersion", "apps/v1");
        root.put("kind", "Deployment");

        ObjectNode metadata = root.putObject("metadata");
        metadata.put("name", id.value());
        metadata.put("namespace", config.namespace());
        putLabels(metadata.putObject("labels"), id);

        ObjectNode deploymentSpec = root.putObject("spec");
        deploymentSpec.put("replicas", spec.replicas().min());
        putLabels(deploymentSpec.putObject("selector").putObject("matchLabels"), id);

        ObjectNode template = deploymentSpec.putObject("template");
        putLabels(template.putObject("metadata").putObject("labels"), id);

        ObjectNode container = template.putObject("spec").putArray("containers").addObject();
        container.put("name", id.value());
        container.put("image", spec.image());

        ObjectNode port = container.putArray("ports").addObject();
        port.put("name", PORT_NAME);
        port.put("containerPort", config.containerPort());

        ObjectNode env = container.putArray("env").addObject();
        env.put("name", PORT_ENV);
        env.put("value", String.valueOf(config.containerPort()));

        ObjectNode resources = container.putObject("resources");
        ObjectNode requests = resources.putObject("requests");
        requests.put("cpu", cpu);
        requests.put("memory", memory);
        ObjectNode limits = resources.putObject("limits");
        limits.put("cpu", cpu);
        limits.put("memory", memory);
        return root;
    }

    private static void putLabels(ObjectNode labels, ServiceId id) {
        labels.put(LABEL_MANAGED_BY, MANAGED_BY_VALUE);
        labels.put(LABEL_SERVICE_ID, id.value());
    }

    // ------------------------------------------------------------------
    // Observation
    // ------------------------------------------------------------------

    private List<JsonNode> fetchPods(ServiceId id) {
        HttpResponse<String> response = send(getRequest(
                "/api/v1/namespaces/" + config.namespace() + "/pods?labelSelector=" + selectorQuery(id)));
        if (response.statusCode() == 404) {
            return List.of();
        }
        requireSuccess(response, "listAllocations(" + id + ")");
        List<JsonNode> pods = new ArrayList<>();
        parseJson(response).path("items").forEach(pods::add);
        return pods;
    }

    /** Un pod en cours d'arret porte un deletionTimestamp : il ne compte plus. */
    private static boolean isTerminating(JsonNode pod) {
        return pod.path("metadata").hasNonNull("deletionTimestamp");
    }

    private static boolean isReady(JsonNode pod) {
        for (JsonNode condition : pod.path("status").path("conditions")) {
            if ("Ready".equals(textOrEmpty(condition, "type"))) {
                return "True".equals(textOrEmpty(condition, "status"));
            }
        }
        return false;
    }

    /** Phase d'un pod -> statut d'allocation (07, « Comptage et statuts »). */
    static String allocationStatus(String phase) {
        return switch (phase) {
            case "Running" -> "running";
            case "Pending" -> "pending";
            case "Succeeded" -> "complete";
            case "Failed" -> "failed";
            case "Unknown" -> "lost";
            default -> "unknown";
        };
    }

    private static Instant podStart(JsonNode pod) {
        String start = textOrEmpty(pod.path("status"), "startTime");
        if (start.isEmpty()) {
            start = textOrEmpty(pod.path("metadata"), "creationTimestamp");
        }
        try {
            return start.isEmpty() ? Instant.EPOCH : Instant.parse(start);
        } catch (java.time.format.DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    // ------------------------------------------------------------------
    // Retour arriere : version semver -> ReplicaSet
    // ------------------------------------------------------------------

    private Optional<JsonNode> findTemplateForVersion(ServiceId id, ServiceVersion targetVersion) {
        HttpResponse<String> response = send(getRequest(
                "/apis/apps/v1/namespaces/" + config.namespace() + "/replicasets?labelSelector="
                        + selectorQuery(id)));
        requireSuccess(response, "rollback:lookup(" + id + ")");

        String expectedSuffix = ":" + targetVersion;
        JsonNode best = null;
        long bestRevision = Long.MIN_VALUE;
        for (JsonNode replicaSet : parseJson(response).path("items")) {
            if (!isOwnedByDeployment(replicaSet, id) || !hasImageEndingWith(replicaSet, expectedSuffix)) {
                continue;
            }
            long revision = parseRevision(replicaSet);
            if (best == null || revision > bestRevision) {
                best = replicaSet;
                bestRevision = revision;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        ObjectNode template = best.path("spec").path("template").deepCopy();
        JsonNode labels = template.path("metadata").path("labels");
        if (labels instanceof ObjectNode labelNode) {
            labelNode.remove(POD_TEMPLATE_HASH_LABEL);
        }
        return Optional.of(template);
    }

    private static boolean isOwnedByDeployment(JsonNode replicaSet, ServiceId id) {
        for (JsonNode owner : replicaSet.path("metadata").path("ownerReferences")) {
            if ("Deployment".equals(textOrEmpty(owner, "kind")) && id.value().equals(textOrEmpty(owner, "name"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasImageEndingWith(JsonNode replicaSet, String suffix) {
        for (JsonNode container : replicaSet.path("spec").path("template").path("spec").path("containers")) {
            if (textOrEmpty(container, "image").endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static long parseRevision(JsonNode replicaSet) {
        String raw = textOrEmpty(replicaSet.path("metadata").path("annotations"), REVISION_ANNOTATION);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    // ------------------------------------------------------------------
    // HTTP bas niveau
    // ------------------------------------------------------------------

    private String deploymentPath(ServiceId id) {
        return "/apis/apps/v1/namespaces/" + config.namespace() + "/deployments/" + id.value();
    }

    private static String selectorQuery(ServiceId id) {
        return URLEncoder.encode(LABEL_SERVICE_ID + "=" + id.value(), StandardCharsets.UTF_8);
    }

    private HttpRequest.Builder baseRequest(String pathAndQuery) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        if (config.token() != null && !config.token().isBlank()) {
            builder.header("Authorization", "Bearer " + config.token());
        }
        return builder;
    }

    private HttpRequest getRequest(String pathAndQuery) {
        return baseRequest(pathAndQuery).GET().build();
    }

    private HttpRequest patch(String path, String query, String contentType, JsonNode body) {
        return baseRequest(path + query)
                .header("Content-Type", contentType)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(writeJson(body)))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DeploymentException("Appel Kubernetes impossible (" + request.method() + " "
                    + request.uri() + ") : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeploymentException("Appel Kubernetes interrompu (" + request.method() + " "
                    + request.uri() + ")", e);
        }
    }

    private void requireSuccess(HttpResponse<String> response, String context) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DeploymentException("Kubernetes a rejete " + context + " : HTTP "
                    + response.statusCode() + " — " + response.body());
        }
    }

    /** Pour stop()/remove() : un Deployment deja absent (404) n'est pas une erreur — idempotence (04.3). */
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
            throw new DeploymentException("Reponse Kubernetes illisible : " + e.getMessage(), e);
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new DeploymentException("Serialisation JSON impossible : " + e.getMessage(), e);
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static SSLContext sslContextTrusting(KubernetesConfig config) {
        try (InputStream pem = Files.newInputStream(config.caCertPath())) {
            KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
            trust.load(null, null);
            int index = 0;
            for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(pem)) {
                trust.setCertificateEntry("egen-kubernetes-ca-" + index++, certificate);
            }
            if (index == 0) {
                throw new DeploymentException("Aucun certificat dans " + config.caCertPath());
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trust);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            return context;
        } catch (IOException | GeneralSecurityException e) {
            throw new DeploymentException("Certificat d'autorite Kubernetes illisible ("
                    + config.caCertPath() + ") : " + e.getMessage(), e);
        }
    }
}
