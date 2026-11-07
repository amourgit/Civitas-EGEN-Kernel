package africa.civitas.egen.adapter.kubernetes;

import africa.civitas.egen.adapter.kubernetes.FakeKubernetesApi.Recorded;
import africa.civitas.egen.adapter.kubernetes.FakeKubernetesApi.Reply;
import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentException;
import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveaux 1 et 2 (docs/architecture/17) : ce que l'adapter envoie REELLEMENT a l'API Kubernetes et la
 * facon dont il lit ses reponses, contre un faux serveur. Le niveau 3 (vrai cluster) reste a faire.
 */
class KubernetesDeploymentAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final String DEPLOYMENT_PATH = "/apis/apps/v1/namespaces/egen/deployments/news-service";

    private FakeKubernetesApi api;
    private KubernetesDeploymentAdapter adapter;

    @BeforeEach
    void start() {
        api = new FakeKubernetesApi();
        adapter = new KubernetesDeploymentAdapter(
                new KubernetesConfig(api.address(), "egen", "tok-123", null, 8080));
    }

    @AfterEach
    void stop() {
        api.close();
    }

    private static DeploymentSpec spec(String image) {
        return new DeploymentSpec("kubernetes", image, "500m", "512Mi", new ReplicaRange(2, 4));
    }

    private Recorded only() {
        assertEquals(1, api.requests().size(), api.requests().toString());
        return api.requests().get(0);
    }

    private static JsonNode body(Recorded request) throws IOException {
        return JSON.readTree(request.body());
    }

    // ---- create / update -------------------------------------------------

    @Test
    void createSendsAServerSideApplyOfOneDeployment() throws IOException {
        adapter.create(NEWS, spec("registry.civitas.africa/news-service:2.4.0"));

        Recorded request = only();
        assertEquals("PATCH", request.method());
        assertEquals(DEPLOYMENT_PATH, request.path());
        assertEquals("fieldManager=egen-kernel&force=true", request.rawQuery());
        assertEquals("application/apply-patch+yaml", request.contentType());
        assertEquals("Bearer tok-123", request.authorization());

        JsonNode deployment = body(request);
        assertEquals("apps/v1", deployment.path("apiVersion").asText());
        assertEquals("Deployment", deployment.path("kind").asText());
        assertEquals("news-service", deployment.path("metadata").path("name").asText());
        assertEquals("egen", deployment.path("metadata").path("namespace").asText());
        assertEquals(2, deployment.path("spec").path("replicas").asInt());
    }

    @Test
    void deploymentHasOneContainerWithResourcesPortAndConsistentLabels() throws IOException {
        adapter.create(NEWS, spec("registry.civitas.africa/news-service:2.4.0"));

        JsonNode deployment = body(only());
        JsonNode selector = deployment.path("spec").path("selector").path("matchLabels");
        JsonNode podLabels = deployment.path("spec").path("template").path("metadata").path("labels");
        assertEquals(selector, podLabels, "le selecteur doit correspondre exactement aux etiquettes des pods");
        assertEquals("news-service", selector.path("egen.civitas.africa/service-id").asText());
        assertEquals("egen-kernel", selector.path("app.kubernetes.io/managed-by").asText());
        // Espace reserve a la Plateforme : le Kernel n'y ecrit jamais (07, « Objets crees »).
        assertFalse(deployment.toString().contains("platform.civitas.africa"));

        JsonNode containers = deployment.path("spec").path("template").path("spec").path("containers");
        assertEquals(1, containers.size());
        JsonNode container = containers.get(0);
        assertEquals("news-service", container.path("name").asText());
        assertEquals("registry.civitas.africa/news-service:2.4.0", container.path("image").asText());
        assertEquals("http", container.path("ports").get(0).path("name").asText());
        assertEquals(8080, container.path("ports").get(0).path("containerPort").asInt());
        assertEquals("EGEN_PORT_http", container.path("env").get(0).path("name").asText());
        assertEquals("8080", container.path("env").get(0).path("value").asText());
        assertEquals("500m", container.path("resources").path("requests").path("cpu").asText());
        assertEquals("512Mi", container.path("resources").path("requests").path("memory").asText());
        assertEquals(container.path("resources").path("requests"), container.path("resources").path("limits"));
    }

    @Test
    void podsCarryTheDocumentedSafeSecurityDefaultsAndNeverRunPrivileged() throws IOException {
        adapter.create(NEWS, spec("registry.civitas.africa/news-service:2.4.0"));

        JsonNode podSpec = body(only()).path("spec").path("template").path("spec");
        assertEquals("RuntimeDefault", podSpec.path("securityContext").path("seccompProfile").path("type").asText());
        assertTrue(podSpec.path("automountServiceAccountToken").isBoolean());
        assertFalse(podSpec.path("automountServiceAccountToken").asBoolean(), "pas de jeton de compte de service dans les pods");
        JsonNode containerSecurity = podSpec.path("containers").get(0).path("securityContext");
        assertTrue(containerSecurity.path("allowPrivilegeEscalation").isBoolean());
        assertFalse(containerSecurity.path("allowPrivilegeEscalation").asBoolean());
        assertFalse(body(api.requests().get(0)).toString().contains("\"privileged\""), "jamais de conteneur privilegie");
    }

    @Test
    void createIsIdempotentTwoCallsTwoIdenticalRequestsNoError() {
        adapter.create(NEWS, spec("registry/news:1.0.0"));
        adapter.create(NEWS, spec("registry/news:1.0.0"));

        List<Recorded> requests = api.requests();
        assertEquals(2, requests.size());
        assertEquals(requests.get(0).body(), requests.get(1).body());
    }

    @Test
    void updateUsesTheSameApplyAsCreate() throws IOException {
        adapter.update(NEWS, spec("registry/news:2.5.0"));

        Recorded request = only();
        assertEquals("PATCH", request.method());
        assertEquals("application/apply-patch+yaml", request.contentType());
        assertEquals("registry/news:2.5.0", body(request).path("spec").path("template").path("spec")
                .path("containers").get(0).path("image").asText());
    }

    @Test
    void invalidQuantityIsRefusedBeforeAnyNetworkCall() {
        DeploymentSpec bad = new DeploymentSpec("kubernetes", "registry/news:1.0.0", "0.5", "512Mi", new ReplicaRange(1, 1));
        assertThrows(DeploymentException.class, () -> adapter.create(NEWS, bad));
        assertTrue(api.requests().isEmpty());
    }

    @Test
    void apiRejectionBecomesADeploymentExceptionWithTheStatus() {
        api.respondWith(r -> new Reply(422, "{\"message\":\"Unprocessable\"}"));
        DeploymentException e = assertThrows(DeploymentException.class,
                () -> adapter.create(NEWS, spec("registry/news:1.0.0")));
        assertTrue(e.getMessage().contains("HTTP 422"), e.getMessage());
        assertFalse(e.getMessage().contains("tok-123"), "le jeton ne doit jamais apparaitre dans une erreur");
    }

    @Test
    void unreachableApiBecomesADeploymentException() {
        api.close();
        assertThrows(DeploymentException.class, () -> adapter.create(NEWS, spec("registry/news:1.0.0")));
    }

    // ---- scale / restart / stop / remove ---------------------------------

    @Test
    void scalePatchesTheScaleSubresource() throws IOException {
        adapter.scale(NEWS, 3);

        Recorded request = only();
        assertEquals("PATCH", request.method());
        assertEquals(DEPLOYMENT_PATH + "/scale", request.path());
        assertEquals("application/merge-patch+json", request.contentType());
        assertEquals(3, body(request).path("spec").path("replicas").asInt());
    }

    @Test
    void restartChangesThePodTemplateAnnotation() throws IOException {
        adapter.restart(NEWS);

        Recorded request = only();
        assertEquals(DEPLOYMENT_PATH, request.path());
        assertEquals("application/merge-patch+json", request.contentType());
        assertFalse(body(request).path("spec").path("template").path("metadata").path("annotations")
                .path("kubectl.kubernetes.io/restartedAt").asText().isEmpty());
    }

    @Test
    void stopScalesToZeroAndToleratesAnAbsentDeployment() throws IOException {
        adapter.stop(NEWS);
        assertEquals(0, body(only()).path("spec").path("replicas").asInt());

        api.respondWith(r -> Reply.notFound());
        adapter.stop(NEWS); // idempotent : 404 n'est pas une erreur
    }

    @Test
    void removeDeletesInBackgroundAndToleratesAnAbsentDeployment() {
        adapter.remove(NEWS);
        Recorded request = only();
        assertEquals("DELETE", request.method());
        assertEquals(DEPLOYMENT_PATH, request.path());
        assertEquals("propagationPolicy=Background", request.rawQuery());

        api.respondWith(r -> Reply.notFound());
        adapter.remove(NEWS);
    }

    // ---- observation ------------------------------------------------------

    private static String pod(String name, String phase, String ready, String ip, boolean terminating) {
        return "{\"metadata\":{\"name\":\"" + name + "\""
                + (terminating ? ",\"deletionTimestamp\":\"2026-11-03T10:00:00Z\"" : "")
                + ",\"creationTimestamp\":\"2026-11-03T09:00:00Z\"},"
                + "\"status\":{\"phase\":\"" + phase + "\",\"podIP\":\"" + ip + "\","
                + "\"startTime\":\"2026-11-03T09:00:05Z\","
                + "\"conditions\":[{\"type\":\"Ready\",\"status\":\"" + ready + "\"}]}}";
    }

    private void servePods(int desired, String... pods) {
        api.respondWith(r -> {
            if (r.path().endsWith("/pods")) {
                return Reply.ok("{\"items\":[" + String.join(",", pods) + "]}");
            }
            return Reply.ok("{\"spec\":{\"replicas\":" + desired + "}}");
        });
    }

    @Test
    void getStatusCountsRunningReadyAndFailedPodsAndIgnoresTerminatingOnes() {
        servePods(5,
                pod("p1", "Running", "True", "10.0.0.1", false),
                pod("p2", "Running", "False", "10.0.0.2", false),   // CrashLoop / pas encore pret
                pod("p3", "Failed", "False", "", false),
                pod("p4", "Unknown", "False", "", false),
                pod("p5", "Pending", "False", "", false),
                pod("p6", "Running", "True", "10.0.0.6", true));    // en cours d'arret : ignore

        DeploymentObservation observation = adapter.getStatus(NEWS);

        assertEquals(5, observation.desiredCount());
        assertEquals(2, observation.runningCount());
        assertEquals(1, observation.healthyCount());
        assertEquals(2, observation.failedCount());
        assertFalse(observation.isConverged());
    }

    @Test
    void getStatusIsConvergedWhenEveryDesiredPodIsReady() {
        servePods(2, pod("p1", "Running", "True", "10.0.0.1", false), pod("p2", "Running", "True", "10.0.0.2", false));
        assertTrue(adapter.getStatus(NEWS).isConverged());
    }

    @Test
    void getStatusOnAnUnknownDeploymentIsEmptyNotAnError() {
        api.respondWith(r -> Reply.notFound());
        assertEquals(DeploymentObservation.empty(), adapter.getStatus(NEWS));
    }

    @Test
    void getStatusSelectsPodsByTheServiceIdLabel() {
        servePods(1, pod("p1", "Running", "True", "10.0.0.1", false));
        adapter.getStatus(NEWS);
        Recorded podsRequest = api.requests().stream().filter(r -> r.path().endsWith("/pods")).findFirst().orElseThrow();
        assertEquals("/api/v1/namespaces/egen/pods", podsRequest.path());
        assertEquals("labelSelector=egen.civitas.africa%2Fservice-id%3Dnews-service", podsRequest.rawQuery());
    }

    @Test
    void listAllocationsGivesAddressAndPortOnlyToRunningPods() {
        servePods(3,
                pod("p1", "Running", "True", "10.0.0.1", false),
                pod("p2", "Pending", "False", "", false),
                pod("p3", "Failed", "False", "10.0.0.3", false),
                pod("p4", "Running", "True", "10.0.0.4", true));

        List<AllocationInfo> allocations = adapter.listAllocations(NEWS);

        assertEquals(3, allocations.size(), "le pod en cours d'arret n'est pas une allocation");
        AllocationInfo running = allocations.get(0);
        assertEquals("p1", running.allocationId());
        assertEquals("running", running.status());
        assertEquals("10.0.0.1", running.address());
        assertEquals(8080, running.port());
        assertTrue(running.hasNetworkInfo());
        assertEquals("pending", allocations.get(1).status());
        assertFalse(allocations.get(1).hasNetworkInfo());
        assertEquals("failed", allocations.get(2).status());
        assertFalse(allocations.get(2).hasNetworkInfo(), "un pod en echec n'est jamais joignable");
    }

    // ---- rollback ----------------------------------------------------------

    private static String replicaSet(String owner, String ownerKind, String image, String revision, String hash) {
        return "{\"metadata\":{\"name\":\"rs-" + revision + "\",\"annotations\":{\"deployment.kubernetes.io/revision\":\""
                + revision + "\"},\"ownerReferences\":[{\"kind\":\"" + ownerKind + "\",\"name\":\"" + owner + "\"}]},"
                + "\"spec\":{\"template\":{\"metadata\":{\"labels\":{\"egen.civitas.africa/service-id\":\"news-service\","
                + "\"pod-template-hash\":\"" + hash + "\",\"marker\":\"rev-" + revision + "\"}},\"spec\":{\"containers\":[{\"name\":\"news-service\","
                + "\"image\":\"" + image + "\"}]}}}}";
    }

    private void serveReplicaSets(String... replicaSets) {
        api.respondWith(r -> "GET".equals(r.method())
                ? Reply.ok("{\"items\":[" + String.join(",", replicaSets) + "]}")
                : Reply.ok("{}"));
    }

    @Test
    void rollbackReplacesTheTemplateWithTheMatchingReplicaSetWithoutItsHashLabel() throws IOException {
        serveReplicaSets(
                replicaSet("news-service", "Deployment", "registry/news:2.4.0", "1", "aaa"),
                replicaSet("news-service", "Deployment", "registry/news:2.5.0", "2", "bbb"));

        adapter.rollback(NEWS, ServiceVersion.parse("2.4.0"));

        Recorded get = api.requests().get(0);
        assertEquals("GET", get.method());
        assertEquals("/apis/apps/v1/namespaces/egen/replicasets", get.path());
        Recorded patch = api.requests().get(1);
        assertEquals("PATCH", patch.method());
        assertEquals(DEPLOYMENT_PATH, patch.path());
        assertEquals("application/json-patch+json", patch.contentType());
        JsonNode operation = JSON.readTree(patch.body()).get(0);
        assertEquals("replace", operation.path("op").asText());
        assertEquals("/spec/template", operation.path("path").asText());
        JsonNode template = operation.path("value");
        assertEquals("registry/news:2.4.0", template.path("spec").path("containers").get(0).path("image").asText());
        assertFalse(template.path("metadata").path("labels").has("pod-template-hash"));
        assertEquals("news-service", template.path("metadata").path("labels").path("egen.civitas.africa/service-id").asText());
    }

    @Test
    void rollbackPicksTheHighestRevisionWhenSeveralReplicaSetsMatch() throws IOException {
        serveReplicaSets(
                replicaSet("news-service", "Deployment", "registry/news:2.4.0", "1", "aaa"),
                replicaSet("news-service", "Deployment", "registry/news:2.4.0", "7", "ccc"),
                replicaSet("news-service", "Deployment", "registry/news:2.4.0", "3", "bbb"));

        adapter.rollback(NEWS, ServiceVersion.parse("2.4.0"));

        assertEquals(2, api.requests().size());
        JsonNode template = JSON.readTree(api.requests().get(1).body()).get(0).path("value");
        // Le modele pose est bien celui de la revision 7 (la plus haute), pas de la 1 ni de la 3.
        assertEquals("rev-7", template.path("metadata").path("labels").path("marker").asText());
    }

    @Test
    void rollbackIgnoresReplicaSetsOwnedByAnotherDeployment() {
        serveReplicaSets(replicaSet("other-service", "Deployment", "registry/news:2.4.0", "1", "aaa"));

        assertThrows(DeploymentException.class, () -> adapter.rollback(NEWS, ServiceVersion.parse("2.4.0")));
        assertEquals(1, api.requests().size(), "aucun PATCH ne doit partir quand aucune version ne correspond");
    }

    @Test
    void rollbackToAnUnknownVersionFailsWithoutPatching() {
        serveReplicaSets(replicaSet("news-service", "Deployment", "registry/news:2.5.0", "2", "bbb"));

        DeploymentException e = assertThrows(DeploymentException.class,
                () -> adapter.rollback(NEWS, ServiceVersion.parse("1.0.0")));
        assertTrue(e.getMessage().contains("1.0.0"));
        assertEquals(1, api.requests().size());
    }
}
