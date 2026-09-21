package africa.civitas.egen.adapter.nomad;

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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation du {@link DeploymentPort} contre l'API HTTP Nomad reelle —
 * voir docs/architecture/07-ports-et-adapters.md, "Deployment Port + Nomad
 * Adapter" pour le mapping complet et les pieges connus. Seul point du
 * Kernel a parler HTTP a Nomad : le domaine et l'application n'en savent
 * jamais rien (garde-fou n2,
 * docs/architecture/02-principes-fondamentaux.md).
 *
 * <p>Un {@code ServiceManifest} EGEN devient un {@code Job} Nomad avec un
 * seul {@code TaskGroup} (mapping 1:1, recommandation V1 — voir 07.1.2).</p>
 */
public final class NomadDeploymentAdapter implements DeploymentPort {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String DOCKER_DRIVER = "docker";
    private static final String DEFAULT_DATACENTER = "dc1";

    private final URI baseUri;
    private final String nomadToken; // nullable — voir docs/architecture/14-securite.md (ACL scoped, jamais le token root)
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public NomadDeploymentAdapter(URI baseUri, String nomadToken) {
        this.baseUri = baseUri;
        this.nomadToken = nomadToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    // ------------------------------------------------------------------
    // DeploymentPort
    // ------------------------------------------------------------------

    @Override
    public DeploymentHandle create(ServiceId id, DeploymentSpec spec) {
        submitJob(id, spec);
        return new DeploymentHandle(id, id.value());
    }

    @Override
    public void update(ServiceId id, DeploymentSpec newSpec) {
        // PUT /v1/jobs est le meme endpoint pour creer et mettre a jour un job —
        // Nomad gere lui-meme le rolling update via le bloc "Update" du job
        // (voir 07.1.2) : ne jamais reimplementer cette logique ici.
        submitJob(id, newSpec);
    }

    @Override
    public void scale(ServiceId id, int replicas) {
        ObjectNode body = mapper.createObjectNode();
        body.put("Count", replicas);
        body.put("Reason", "egen-scale");
        ObjectNode target = body.putObject("Target");
        target.put("Group", id.value());

        HttpResponse<String> response = send(postJson("/v1/job/" + id.value() + "/scale", body));
        requireSuccess(response, "scale(" + id + ")");
    }

    @Override
    public void restart(ServiceId id) {
        ObjectNode body = mapper.createObjectNode();
        body.put("JobID", id.value());
        ObjectNode options = body.putObject("EvalOptions");
        options.put("ForceReschedule", true);

        HttpResponse<String> response = send(postJson("/v1/job/" + id.value() + "/evaluate", body));
        requireSuccess(response, "restart(" + id + ")");
    }

    @Override
    public void stop(ServiceId id) {
        // purge=false (defaut) : Nomad conserve l'historique du job pour audit —
        // voir 07.1.2, la purge complete reste reservee a remove().
        HttpResponse<String> response = send(deleteRequest("/v1/job/" + id.value()));
        requireSuccessOrAlreadyGone(response, "stop(" + id + ")");
    }

    @Override
    public void remove(ServiceId id) {
        HttpResponse<String> response = send(deleteRequest("/v1/job/" + id.value() + "?purge=true"));
        requireSuccessOrAlreadyGone(response, "remove(" + id + ")");
    }

    @Override
    public DeploymentObservation getStatus(ServiceId id) {
        HttpResponse<String> jobResponse = send(getRequest("/v1/job/" + id.value()));
        if (jobResponse.statusCode() == 404) {
            return DeploymentObservation.empty();
        }
        requireSuccess(jobResponse, "getStatus(" + id + ")");
        int desiredCount = sumTaskGroupCounts(parseJson(jobResponse));

        List<JsonNode> allocations = fetchAllocations(id);
        int running = 0;
        int failed = 0;
        for (JsonNode allocation : allocations) {
            String clientStatus = textOrEmpty(allocation, "ClientStatus");
            if ("running".equals(clientStatus)) {
                running++;
            } else if ("failed".equals(clientStatus) || "lost".equals(clientStatus)) {
                failed++;
            }
        }
        // V1 : "healthy" est assimile a "running" — la nuance apportee par un
        // vrai health check applicatif releve du Discovery Adapter (Consul),
        // cable en Phase 2 (voir docs/architecture/19-feuille-de-route.md).
        int healthy = running;

        return new DeploymentObservation(desiredCount, running, healthy, failed, Instant.now());
    }

    @Override
    public List<AllocationInfo> listAllocations(ServiceId id) {
        List<AllocationInfo> result = new ArrayList<>();
        for (JsonNode allocation : fetchAllocations(id)) {
            String allocationId = textOrEmpty(allocation, "ID");
            String status = textOrEmpty(allocation, "ClientStatus");
            Instant since = allocation.hasNonNull("CreateTime")
                    ? Instant.ofEpochSecond(0, allocation.get("CreateTime").asLong())
                    : Instant.EPOCH;
            NetworkEndpoint endpoint = extractNetworkEndpoint(allocation);
            if (!allocationId.isEmpty()) {
                result.add(new AllocationInfo(allocationId, status.isEmpty() ? "unknown" : status,
                        since, endpoint.address(), endpoint.port()));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Extrait l'adresse/port reseau d'une allocation Nomad — les ports
     * dynamiques sont attribues par le scheduler et ne sont connus qu'a
     * l'execution (voir {@code Resources.Networks[].DynamicPorts} dans la
     * reponse de l'API Nomad). Retourne une adresse/port vides si la
     * structure attendue est absente (allocation pas encore planifiee) —
     * l'appelant (la boucle de reconciliation) gere cette absence sans
     * lever d'exception, voir {@code AllocationInfo.hasNetworkInfo()}.
     */
    private static NetworkEndpoint extractNetworkEndpoint(JsonNode allocation) {
        JsonNode networks = allocation.path("Resources").path("Networks");
        if (!networks.isArray() || networks.isEmpty()) {
            return new NetworkEndpoint("", 0);
        }
        JsonNode network = networks.get(0);
        String address = textOrEmpty(network, "IP");
        JsonNode dynamicPorts = network.path("DynamicPorts");
        int port = 0;
        if (dynamicPorts.isArray() && !dynamicPorts.isEmpty()) {
            port = dynamicPorts.get(0).path("Value").asInt(0);
        }
        return new NetworkEndpoint(address, port);
    }

    private record NetworkEndpoint(String address, int port) {
    }

    @Override
    public void rollback(ServiceId id, ServiceVersion targetVersion) {
        int nomadJobVersion = findNomadJobVersionForImageTag(id, targetVersion)
                .orElseThrow(() -> new DeploymentException(
                        "Aucune version Nomad de \"" + id + "\" ne correspond a l'image taguee \""
                                + targetVersion + "\" — impossible de revert"));

        ObjectNode body = mapper.createObjectNode();
        body.put("JobID", id.value());
        body.put("JobVersion", nomadJobVersion);

        HttpResponse<String> response = send(postJson("/v1/job/" + id.value() + "/revert", body));
        requireSuccess(response, "rollback(" + id + ", " + targetVersion + ")");
    }

    // ------------------------------------------------------------------
    // Traduction ServiceManifest/DeploymentSpec -> Job Nomad
    // ------------------------------------------------------------------

    private void submitJob(ServiceId id, DeploymentSpec spec) {
        ObjectNode job = buildJobJson(id, spec);
        ObjectNode body = mapper.createObjectNode();
        body.set("Job", job);

        HttpResponse<String> response = send(putJson("/v1/jobs", body));
        requireSuccess(response, "create/update(" + id + ")");
    }

    private ObjectNode buildJobJson(ServiceId id, DeploymentSpec spec) {
        ObjectNode job = mapper.createObjectNode();
        job.put("ID", id.value());
        job.put("Name", id.value());
        job.put("Type", "service");
        ArrayNode datacenters = job.putArray("Datacenters");
        datacenters.add(DEFAULT_DATACENTER);

        ArrayNode taskGroups = job.putArray("TaskGroups");
        ObjectNode group = taskGroups.addObject();
        group.put("Name", id.value());
        group.put("Count", spec.replicas().min());

        // Reserve un port dynamique par allocation, pour que chaque instance
        // ait une adresse:port a rapporter au Discovery Adapter (voir
        // DeploymentPort.listAllocations -> ReconciliationEngine). Le
        // mapping vers le port reellement ecoute par le conteneur applicatif
        // arrivera avec network.endpoints du manifeste (non encore dans le
        // domaine — voir docs/architecture/19-feuille-de-route.md) ; en
        // attendant, ce port reserve suffit a exercer l'enregistrement
        // Discovery de bout en bout.
        ArrayNode networks = group.putArray("Networks");
        ObjectNode network = networks.addObject();
        ArrayNode dynamicPorts = network.putArray("DynamicPorts");
        ObjectNode httpPort = dynamicPorts.addObject();
        httpPort.put("Label", "http");

        ArrayNode tasks = group.putArray("Tasks");
        ObjectNode task = tasks.addObject();
        task.put("Name", id.value());
        task.put("Driver", DOCKER_DRIVER);

        ObjectNode config = task.putObject("Config");
        config.put("image", spec.image());

        ObjectNode resources = task.putObject("Resources");
        resources.put("CPU", NomadResourceUnits.parseCpuMhz(spec.cpu()));
        resources.put("MemoryMB", NomadResourceUnits.parseMemoryMb(spec.memory()));

        return job;
    }

    // ------------------------------------------------------------------
    // Rollback : resolution semver -> Nomad JobVersion (entier sequentiel)
    // ------------------------------------------------------------------

    private Optional<Integer> findNomadJobVersionForImageTag(ServiceId id, ServiceVersion targetVersion) {
        HttpResponse<String> response = send(getRequest("/v1/job/" + id.value() + "/versions"));
        requireSuccess(response, "rollback:lookup(" + id + ")");
        JsonNode root = parseJson(response);
        JsonNode versions = root.path("Versions");
        String expectedTagSuffix = ":" + targetVersion;

        for (JsonNode version : versions) {
            for (JsonNode group : version.path("TaskGroups")) {
                for (JsonNode task : group.path("Tasks")) {
                    String image = textOrEmpty(task.path("Config"), "image");
                    if (image.endsWith(expectedTagSuffix)) {
                        return Optional.of(version.path("Version").asInt());
                    }
                }
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // HTTP bas niveau
    // ------------------------------------------------------------------

    private List<JsonNode> fetchAllocations(ServiceId id) {
        HttpResponse<String> response = send(getRequest("/v1/job/" + id.value() + "/allocations?all=true"));
        if (response.statusCode() == 404) {
            return List.of();
        }
        requireSuccess(response, "listAllocations(" + id + ")");
        List<JsonNode> result = new ArrayList<>();
        parseJson(response).forEach(result::add);
        return result;
    }

    private HttpRequest.Builder baseRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(REQUEST_TIMEOUT);
        if (nomadToken != null && !nomadToken.isBlank()) {
            builder.header("X-Nomad-Token", nomadToken);
        }
        return builder;
    }

    private HttpRequest getRequest(String path) {
        return baseRequest(path).GET().build();
    }

    private HttpRequest deleteRequest(String path) {
        return baseRequest(path).DELETE().build();
    }

    private HttpRequest putJson(String path, ObjectNode body) {
        return baseRequest(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(writeJson(body)))
                .build();
    }

    private HttpRequest postJson(String path, ObjectNode body) {
        return baseRequest(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(body)))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DeploymentException("Appel Nomad impossible (" + request.method() + " "
                    + request.uri() + ") : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeploymentException("Appel Nomad interrompu (" + request.method() + " "
                    + request.uri() + ")", e);
        }
    }

    private void requireSuccess(HttpResponse<String> response, String context) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DeploymentException("Nomad a rejete " + context + " : HTTP "
                    + response.statusCode() + " — " + response.body());
        }
    }

    /** Pour stop()/remove() : un job deja absent (404) n'est pas une erreur — idempotence (voir 04.3). */
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
            throw new DeploymentException("Reponse Nomad illisible : " + e.getMessage(), e);
        }
    }

    private String writeJson(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new DeploymentException("Serialisation JSON impossible : " + e.getMessage(), e);
        }
    }

    private static int sumTaskGroupCounts(JsonNode job) {
        int total = 0;
        for (JsonNode group : job.path("TaskGroups")) {
            total += group.path("Count").asInt(0);
        }
        return total;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }
}
