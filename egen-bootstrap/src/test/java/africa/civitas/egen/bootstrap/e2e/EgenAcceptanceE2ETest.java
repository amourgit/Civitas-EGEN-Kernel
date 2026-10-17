package africa.civitas.egen.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de niveau 5 : le scenario de docs/architecture/20-scenario-bout-en-bout.md, rejoue contre le VRAI
 * Kernel (demarre par Quarkus, avec ses vrais adapters) et un environnement ephemere complet —
 * PostgreSQL, NATS, Consul, Vault et un Nomad qui execute reellement des allocations
 * ({@link EgenEnvironment}). Aucun double de port.
 *
 * <p>Chaque etape (et chaque numero d'etape du doc 20 cite en commentaire) depend de la precedente : un echec
 * precoce explique les suivants. Les attentes sont des SONDAGES avec delai maximal — jamais un {@code sleep}
 * calibre — et leur echec affiche l'etat de Nomad.</p>
 *
 * <p>Etiquete {@code e2e} : exclu du build par defaut (il exige Docker et plusieurs minutes), execute par
 * {@code mvn -Pe2e verify} et par le job « Acceptation de bout en bout » de la CI.</p>
 */
@QuarkusTest
@QuarkusTestResource(value = EgenEnvironment.class, restrictToAnnotatedClass = true)
@TestSecurity(user = "e2e-admin", roles = "egen-admin")
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EgenAcceptanceE2ETest {

    private static final Duration DEPLOY_TIMEOUT = Duration.ofSeconds(240);
    private static final String EVENT_CREATED = "africa.civitas.news.article.created.v1";
    private static final String EVENT_PUBLISHED = "africa.civitas.news.article.published.v1";
    private static final String EVENT_DELIVERY_FAILED = "africa.civitas.notification.delivery-failed.v1";

    @BeforeAll
    static void yamlEncoding() {
        RestAssured.config = RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    // ------------------------------------------------------------------ fixtures de manifestes

    /** Un service de fixture reel (voir server.py). {@code extraBlocks} : blocs YAML supplementaires, a la racine. */
    private static String manifest(String name, int replicas, String extraBlocks) {
        return """
                apiVersion: egen.civitas.africa/v1
                kind: ServiceManifest
                metadata:
                  name: %s
                  version: 1.0.0
                  team: content-platform
                runtime:
                  type: container
                  language: python
                deployment:
                  adapter: nomad
                  image: %s
                  resources: { cpu: 100m, memory: 64Mi }
                  replicas: { min: %d, max: %d }
                health:
                  http: { endpoint: /health, interval: 5s, timeout: 2s, failuresBeforeUnhealthy: 3 }
                lifecycle:
                  shutdown: { gracePeriod: 5s }
                %s
                """.formatted(name, EgenEnvironment.FIXTURE_IMAGE, replicas, replicas, extraBlocks);
    }

    private static String newsServiceManifest() {
        return manifest("news-service", 2, """
                dependencies:
                  services:
                    - { name: notification-service, versionConstraint: ">=1.0.0", required: true }
                    - { name: storage-service, versionConstraint: ">=1.0.0", required: true }
                  resources:
                    - { name: primary-db, type: postgres, params: { sizeGb: 20 } }
                    - { name: cache, type: redis }
                events:
                  publishes:
                    - { type: %s }
                    - { type: %s }
                  consumes:
                    - type: %s
                      subscriptionGroup: news-service-consumers
                      deadLetter: { maxRetries: 5, strategy: exponential-backoff }
                configuration:
                  required:
                    - { key: FEATURE_FLAG_COMMENTS, type: boolean, default: "false" }
                  secretsRefs:
                    - { key: DB_PASSWORD, provider: vault, path: secret/data/news-service/db }
                interfaces:
                  - { type: openapi, ref: /internal/openapi.json, riskDefault: read }
                """.formatted(EVENT_CREATED, EVENT_PUBLISHED, EVENT_DELIVERY_FAILED));
    }

    /** Le workflow de reference de docs/architecture/11, tel quel. */
    private static final String PUBLISH_ARTICLE_WORKFLOW = """
            apiVersion: egen.civitas.africa/v1
            kind: WorkflowDefinition
            metadata:
              name: publish-article-workflow
            spec:
              steps:
                - id: validate-content
                  service: news-service
                  operation: POST /internal/validate
                  timeout: 10s
                  retry: { maxAttempts: 3, backoff: exponential }
                - id: reserve-notification-slot
                  service: notification-service
                  operation: POST /internal/reserve-slot
                  dependsOn: [validate-content]
                  compensation:
                    operation: POST /internal/release-slot
                - id: publish
                  service: news-service
                  operation: POST /internal/publish
                  dependsOn: [reserve-notification-slot]
                  compensation:
                    operation: POST /internal/unpublish
                - id: notify-subscribers
                  service: notification-service
                  operation: EVENT africa.civitas.news.article.published.v1
                  dependsOn: [publish]
              compensationStrategy: backward
              executionPolicy:
                globalTimeout: 5m
                manualApprovalSteps: []
            """;

    // ------------------------------------------------------------------ appels au Kernel (comme des curl)

    private static void declare(String yaml) {
        given().header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/yaml")
                .body(yaml).when().post("/api/v1/services").then().statusCode(202);
    }

    private static Response status(String service) {
        return given().when().get("/api/v1/services/" + service + "/status");
    }

    private static String phase(String service) {
        Response response = status(service);
        return response.statusCode() == 200 ? response.jsonPath().getString("phase") : "?";
    }

    private static String condition(String service, String type, String field) {
        return status(service).jsonPath().getString("conditions.find { it.type == '" + type + "' }." + field);
    }

    private static void await(String what, Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Delai depasse (" + timeout.toSeconds() + " s) : " + what + EgenEnvironment.diagnostics());
    }

    private static void awaitPhase(String service, String expected, Duration timeout) {
        await(service + " devrait atteindre " + expected + " (phase actuelle : " + phase(service) + ", statut : "
                + status(service).asString() + ")", timeout, () -> expected.equals(phase(service)));
    }

    /** Instances saines resolues pour un service, via la decouverte du Kernel (donc Consul). */
    private static List<String> instances(String service) {
        return given().when().get("/api/v1/discover/" + service).jsonPath()
                .getList("instances.collect { it.address + ':' + it.port }");
    }

    private static JsonNode fixtureCalls(String endpoint) throws Exception {
        return EgenEnvironment.getJson("http://" + endpoint + "/__calls").path("calls");
    }

    private static List<String> allCalls(String service) throws Exception {
        List<String> calls = new ArrayList<>();
        for (String endpoint : instances(service)) {
            fixtureCalls(endpoint).forEach(call -> calls.add(call.asText()));
        }
        return calls;
    }

    private static void fixtureControl(String endpoint, String path) throws Exception {
        assertEquals(200, EgenEnvironment.statusOf("POST", "http://" + endpoint + path, ""));
    }

    // ------------------------------------------------------------------ etape 0 : Kernel vide

    @Test
    @Order(1)
    void emptyKernelIsHealthyAndKnowsNoService() {
        given().when().get("/api/v1/health").then().statusCode(200);
        assertEquals(0, given().when().get("/api/v1/services").then().statusCode(200)
                .extract().jsonPath().getList("$").size(), "aucun service declare dans un Kernel vide");
        assertEquals(List.of(), instances("news-service"), "rien n'est resolu dans un Kernel vide");
    }

    // ------------------------------------------------------------------ etape 3 (secrets) : ConfigResolved

    @Test
    @Order(2)
    void aMissingVaultSecretHoldsTheServiceRegisteredThenItConvergesOnceTheSecretExists() throws Exception {
        declare(manifest("secret-probe-service", 1, """
                configuration:
                  secretsRefs:
                    - { key: DB_PASSWORD, provider: vault, path: secret/data/probe/db }
                """));

        await("ConfigResolved devrait etre FALSE/SecretUnresolved", Duration.ofSeconds(60),
                () -> "SecretUnresolved".equals(condition("secret-probe-service", "ConfigResolved", "reason")));
        assertEquals("REGISTERED", phase("secret-probe-service"), "rien n'est deploye sans les secrets");
        assertEquals(404, EgenEnvironment.statusOf("GET", EgenEnvironment.NOMAD_URL + "/v1/job/secret-probe-service", null),
                "aucun job Nomad tant que la configuration n'est pas resolue");

        putVaultSecret("probe/db"); // l'operateur cree le secret — sans toucher au manifeste

        awaitPhase("secret-probe-service", "RUNNING", DEPLOY_TIMEOUT);
        assertEquals("TRUE", condition("secret-probe-service", "ConfigResolved", "status"));
    }

    // ------------------------------------------------------------------ etapes 1 a 6 : ordre des dependances

    @Test
    @Order(3)
    void aServiceWaitsForItsRequiredDependenciesAndIsDeployedAfterThem() throws Exception {
        putVaultSecret("news-service/db");

        declare(newsServiceManifest()); // declare AVANT ses dependances

        await("DependenciesSatisfied devrait etre FALSE tant que ses dependances n'existent pas",
                Duration.ofSeconds(60), () -> "FALSE".equals(condition("news-service", "DependenciesSatisfied", "status")));
        assertEquals("CONFIGURED", phase("news-service"));
        assertEquals(404, EgenEnvironment.statusOf("GET", EgenEnvironment.NOMAD_URL + "/v1/job/news-service", null),
                "news-service ne doit pas etre deploye avant ses dependances");

        declare(manifest("notification-service", 1, ""));
        declare(manifest("storage-service", 1, ""));

        awaitPhase("notification-service", "RUNNING", DEPLOY_TIMEOUT);
        awaitPhase("storage-service", "RUNNING", DEPLOY_TIMEOUT);
        awaitPhase("news-service", "RUNNING", DEPLOY_TIMEOUT);

        long newsSubmitted = EgenEnvironment.getJson(EgenEnvironment.NOMAD_URL + "/v1/job/news-service").path("SubmitTime").asLong();
        long notificationSubmitted = EgenEnvironment.getJson(EgenEnvironment.NOMAD_URL + "/v1/job/notification-service").path("SubmitTime").asLong();
        long storageSubmitted = EgenEnvironment.getJson(EgenEnvironment.NOMAD_URL + "/v1/job/storage-service").path("SubmitTime").asLong();
        assertTrue(newsSubmitted > notificationSubmitted && newsSubmitted > storageSubmitted,
                "news-service doit etre soumis a Nomad APRES ses dependances");
    }

    // ------------------------------------------------------------------ etape 6 : Conditions, decouverte, flux

    @Test
    @Order(4)
    void aRunningServiceIsFullyBoundToConsulNatsAndItsStatusSaysSo() throws Exception {
        Response status = status("news-service");
        assertEquals("RUNNING", status.jsonPath().getString("phase"));
        assertEquals(1, status.jsonPath().getInt("observedGeneration"));
        for (String type : List.of("ManifestValid", "ConfigResolved", "DependenciesSatisfied",
                "MessagingBindingsReady", "DeploymentReady", "DiscoveryReady")) {
            assertEquals("TRUE", condition("news-service", type, "status"), "Condition " + type + " : " + status.asString());
        }

        // Decouverte (Consul reel) : les deux allocations sont enregistrees et saines.
        await("deux instances saines de news-service dans la decouverte", Duration.ofSeconds(60),
                () -> instances("news-service").size() == 2);
        JsonNode passing = EgenEnvironment.getJson(EgenEnvironment.consulUrl() + "/v1/health/service/news-service?passing=true");
        assertEquals(2, passing.size(), "Consul doit voir deux instances saines : " + passing);

        // Messagerie (NATS reel) : un flux par type d'evenement, publie ou consomme.
        JsonNode jsz = EgenEnvironment.getJson(EgenEnvironment.natsMonitoringUrl() + "/jsz?streams=true");
        List<String> streams = new ArrayList<>();
        jsz.path("account_details").forEach(account -> account.path("stream_detail")
                .forEach(stream -> streams.add(stream.path("name").asText())));
        assertTrue(streams.contains("EGEN-africa~civitas~news~article~created~v1"), "flux du type publie : " + streams);
        assertTrue(streams.contains("EGEN-africa~civitas~notification~delivery-failed~v1"), "flux du type consomme : " + streams);

        // Lecture du manifeste (docs/architecture/13) : le bloc interfaces est restitue, opaque, tel que declare.
        Response manifest = given().when().get("/api/v1/services/news-service");
        assertEquals("openapi", manifest.jsonPath().getString("manifest.interfaces[0].type"));
        assertEquals("/internal/openapi.json", manifest.jsonPath().getString("manifest.interfaces[0].ref"));
        assertEquals("primary-db", manifest.jsonPath().getString("manifest.dependencies.resources[0].name"));
    }

    // ------------------------------------------------------------------ etape 7 : panne et reconvergence

    @Test
    @Order(5)
    void aRealCrashDegradesTheServiceThenTheKernelAndNomadReconverge() throws Exception {
        List<String> before = instances("news-service");
        assertEquals(2, before.size());

        fixtureControl(before.get(0), "/__crash"); // le processus s'arrete brutalement : une vraie panne

        await("news-service devrait passer en DEGRADED", Duration.ofSeconds(90), () -> "DEGRADED".equals(phase("news-service")));
        awaitPhase("news-service", "RUNNING", DEPLOY_TIMEOUT);
        await("deux instances saines a nouveau", Duration.ofSeconds(90), () -> instances("news-service").size() == 2);
    }

    // ------------------------------------------------------------------ partie 2 : workflow et compensation

    @Test
    @Order(6)
    void theReferenceWorkflowRunsToCompletionAcrossRealServicesAndPublishesItsEvent() throws Exception {
        given().header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/yaml")
                .body(PUBLISH_ARTICLE_WORKFLOW).when().post("/api/v1/workflows").then().statusCode(202);

        String execution = runWorkflow("42");

        await("l'execution devrait etre COMPLETED", Duration.ofSeconds(90),
                () -> "COMPLETED".equals(executionStatus(execution)));
        assertTrue(allCalls("news-service").contains("POST /internal/validate"));
        assertTrue(allCalls("news-service").contains("POST /internal/publish"));
        assertTrue(allCalls("notification-service").contains("POST /internal/reserve-slot"));
        assertFalse(allCalls("notification-service").contains("POST /internal/release-slot"));
        // L'etape EVENT a publie dans le flux NATS reel garanti par la declaration de news-service.
        await("l'evenement publie devrait etre dans son flux NATS", Duration.ofSeconds(30),
                () -> streamMessages("EGEN-africa~civitas~news~article~published~v1") >= 1);
    }

    @Test
    @Order(7)
    void aFailingStepCompensatesTheSucceededOnesInReverseOrderOnRealServices() throws Exception {
        for (String endpoint : instances("news-service")) {
            fixtureControl(endpoint, "/__reset");
            fixtureControl(endpoint, "/__fail?path=/internal/publish&status=500"); // l'echec voulu
        }
        for (String endpoint : instances("notification-service")) {
            fixtureControl(endpoint, "/__reset");
        }

        String execution = runWorkflow("43");

        await("l'execution devrait etre COMPENSATED", Duration.ofSeconds(90),
                () -> "COMPENSATED".equals(executionStatus(execution)));

        List<String> notification = allCalls("notification-service");
        assertEquals(List.of("POST /internal/reserve-slot", "POST /internal/release-slot"), notification,
                "release-slot compense reserve-slot, dans cet ordre");
        List<String> news = allCalls("news-service");
        assertTrue(news.contains("POST /internal/validate"));
        assertTrue(news.contains("POST /internal/publish"), "publish a ete tente puis a echoue");
        assertFalse(news.contains("POST /internal/unpublish"), "l'etape qui a echoue n'est jamais compensee");

        Response result = given().when().get("/api/v1/workflows/publish-article-workflow/executions/" + execution);
        assertEquals("COMPENSATED", result.jsonPath().getString("status"));
        assertEquals("FAILED", result.jsonPath().getString("stepResults.find { it.stepId == 'publish' }.status"));
        assertEquals("COMPENSATED", result.jsonPath().getString(
                "stepResults.findAll { it.stepId == 'reserve-notification-slot' }.status.last()"));
    }

    // ------------------------------------------------------------------ utilitaires du scenario

    private static String runWorkflow(String articleId) {
        String location = given().header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON).body("{\"input\": {\"articleId\": \"" + articleId + "\"}}")
                .when().post("/api/v1/workflows/publish-article-workflow/executions")
                .then().statusCode(202).extract().header("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static String executionStatus(String execution) {
        return given().when().get("/api/v1/workflows/publish-article-workflow/executions/" + execution)
                .jsonPath().getString("status");
    }

    private static long streamMessages(String stream) throws Exception {
        JsonNode jsz = EgenEnvironment.getJson(EgenEnvironment.natsMonitoringUrl() + "/jsz?streams=true");
        long total = 0;
        for (JsonNode account : jsz.path("account_details")) {
            for (JsonNode detail : account.path("stream_detail")) {
                if (stream.equals(detail.path("name").asText())) {
                    total += detail.path("state").path("messages").asLong();
                }
            }
        }
        return total;
    }

    /** Cree un secret dans le Vault reel (KV v2 monte sur « secret/ » en mode dev). */
    private static void putVaultSecret(String path) throws Exception {
        int status = EgenEnvironment.statusOf("POST", EgenEnvironment.vaultUrl() + "/v1/secret/data/" + path,
                "{\"data\": {\"password\": \"valeur-de-test\"}}",
                "X-Vault-Token", EgenEnvironment.VAULT_TOKEN, "Content-Type", "application/json");
        assertTrue(status == 200 || status == 204, "creation du secret " + path + " : HTTP " + status);
    }
}
