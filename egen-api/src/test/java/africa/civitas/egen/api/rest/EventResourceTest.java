package africa.civitas.egen.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.EventSpec;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.OwnerTeam;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import africa.civitas.egen.testkit.RecordingMessagingPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrat de {@code POST /api/v1/events/publish} (docs/architecture/12, 13) : 202, 400, 403, 503, et ce qui
 * est reellement relaye au Messaging Port.
 */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = "egen-admin")
class EventResourceTest {

    @Inject
    RecordingMessagingPort messaging;

    @Inject
    RegistryStorePort registry;

    @BeforeAll
    static void yamlEncoding() {
        RestAssured.config = RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    @BeforeEach
    void reset() {
        messaging.clear();
        messaging.failOnPublish(false);
    }

    private static String type(String suffix) {
        return "africa.civitas.it." + suffix + ".v1";
    }

    private static void declarePublisher(String name, String eventType) {
        String yaml = """
                apiVersion: egen.civitas.africa/v1
                kind: ServiceManifest
                metadata:
                  name: %s
                  version: 1.0.0
                  team: content-platform
                runtime:
                  type: container
                  language: java
                deployment:
                  adapter: nomad
                  image: registry.civitas.africa/%s:1.0.0
                  resources: { cpu: 500m, memory: 512Mi }
                  replicas: { min: 1, max: 2 }
                health:
                  http: { endpoint: /health, interval: 10s, timeout: 2s, failuresBeforeUnhealthy: 3 }
                events:
                  publishes:
                    - { type: %s }
                """.formatted(name, name, eventType);
        given().header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/yaml")
                .body(yaml).when().post("/api/v1/services").then().statusCode(202);
    }

    private static io.restassured.specification.RequestSpecification publish(String body) {
        return given().header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON).body(body);
    }

    @Test
    void anAnnouncedEventIsAcceptedAndRelayedToItsSubjectWithOpaqueData() {
        String eventType = type("announced-" + UUID.randomUUID().toString().substring(0, 8));
        declarePublisher("it-pub-" + UUID.randomUUID().toString().substring(0, 8), eventType);

        publish("""
                { "specversion": "1.0", "type": "%s", "source": "/news-service",
                  "egen_correlation_id": "corr-1", "data": { "articleId": "42", "nested": [1, 2] } }
                """.formatted(eventType))
                .when().post("/api/v1/events/publish")
                .then().statusCode(202)
                .body("type", is(eventType))
                .body("egen_correlation_id", is("corr-1"))
                .body("id", notNullValue());

        assertEquals(1, messaging.published().size());
        var sent = messaging.published().get(0);
        assertEquals(eventType, sent.subject());
        assertEquals("corr-1", sent.event().correlationId());
        assertTrue(sent.event().data().contains("\"articleId\":\"42\""), sent.event().data());
        assertTrue(sent.event().data().contains("[1,2]"), sent.event().data());
    }

    /** Inscrit directement dans le Registry un service qui annonce {@code eventType} (equipe informative). */
    private void saveAnnouncedPublisher(String name, String eventType) {
        ServiceManifest manifest = new ServiceManifest(ServiceId.of(name), ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry/" + name + ":1.0.0", "java"),
                new DeploymentSpec("nomad", "registry/" + name + ":1.0.0", "500m", "512Mi", new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(Duration.ofMillis(50)), List.of(), OwnerTeam.of("content-platform"), null,
                new EventSpec(List.of(new EventSpec.Published(eventType, null)), List.of()), null, null);
        registry.save(new DesiredState(manifest, 0L, TargetEnvironment.of("development")));
    }

    @Test
    @TestSecurity(user = "dev", roles = "developer")
    void aCallerWithoutTheAdministratorRoleIs403EvenForAnAnnouncedTypeAndNothingIsPublished() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String announced = type("announced-" + suffix);
        saveAnnouncedPublisher("it-evt-" + suffix, announced);

        publish("{\"type\": \"" + announced + "\", \"source\": \"/x\", \"data\": {}}")
                .when().post("/api/v1/events/publish").then().statusCode(403);
        assertTrue(messaging.published().isEmpty());
    }

    @Test
    @TestSecurity(user = "dev", roles = "developer")
    void theAdministratorCheckComesBeforeTheBodyIsRead() {
        // Corps illisible : un 403 (et non un 400) prouve que le droit passe AVANT la lecture du corps.
        publish("pas du json").when().post("/api/v1/events/publish").then().statusCode(403);
        assertTrue(messaging.published().isEmpty());
    }

    @Test
    void aTypeNoDeclaredServiceAnnouncesIs403AndNothingIsPublished() {
        publish("{\"type\": \"" + type("nobody-" + UUID.randomUUID().toString().substring(0, 8))
                + "\", \"source\": \"/x\", \"data\": {}}")
                .when().post("/api/v1/events/publish").then().statusCode(403);
        assertTrue(messaging.published().isEmpty());
    }

    @Test
    void theIdempotencyKeyIsRequired() {
        given().contentType(ContentType.JSON).body("{\"type\": \"a.b\", \"source\": \"/x\", \"data\": {}}")
                .when().post("/api/v1/events/publish").then().statusCode(400);
    }

    @Test
    void malformedOrIncompleteBodiesAre400() {
        publish("pas du json").when().post("/api/v1/events/publish").then().statusCode(400);
        publish("{\"type\": \"a.b\", \"source\": \"/x\"}").when().post("/api/v1/events/publish").then().statusCode(400);
        publish("{\"type\": \"a.b\", \"data\": {}}").when().post("/api/v1/events/publish").then().statusCode(400);
        publish("{\"type\": \"a.b\", \"source\": \"/x\", \"data\": {}, \"specversion\": \"0.3\"}")
                .when().post("/api/v1/events/publish").then().statusCode(400);
        publish("{\"type\": \"a.b\", \"source\": \"/x\", \"data\": {}, \"unknownField\": 1}")
                .when().post("/api/v1/events/publish").then().statusCode(400);
    }

    @Test
    void anUnavailableBrokerIs503() {
        String eventType = type("down-" + UUID.randomUUID().toString().substring(0, 8));
        declarePublisher("it-down-" + UUID.randomUUID().toString().substring(0, 8), eventType);
        messaging.failOnPublish(true);

        publish("{\"type\": \"" + eventType + "\", \"source\": \"/x\", \"data\": {}}")
                .when().post("/api/v1/events/publish").then().statusCode(503);
        assertTrue(messaging.published().isEmpty());
    }
}
