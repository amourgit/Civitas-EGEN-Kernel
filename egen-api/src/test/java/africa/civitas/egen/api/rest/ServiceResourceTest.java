package africa.civitas.egen.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Verifie le cycle "Declare" -&gt; "Observe" complet au niveau HTTP (voir
 * docs/architecture/20-scenario-bout-en-bout.md, etapes 1 et 8), avec le
 * cablage CDI de test de {@link TestUseCaseProducers} (RegistryStorePort en
 * memoire, aucun Nomad reel requis a ce niveau).
 */
@QuarkusTest
class ServiceResourceTest {

    private static final String MANIFEST_YAML = """
            apiVersion: egen.civitas.africa/v1
            kind: ServiceManifest
            metadata:
              name: it-fixture-service
              version: 1.0.0
            runtime:
              type: container
              artifact: busybox:latest
            deployment:
              adapter: nomad
              image: busybox:latest
              resources:
                cpu: "100m"
                memory: "64Mi"
              replicas:
                min: 1
                max: 1
            health:
              http:
                endpoint: /health
                interval: 10s
                timeout: 2s
                failuresBeforeUnhealthy: 3
            """;

    @Test
    void healthRespondsUp() {
        given()
                .when().get("/api/v1/health")
                .then().statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void declareRespondsAcceptedAndStatusIsThenReadable() {
        String location = given()
                .contentType("application/yaml")
                .body(MANIFEST_YAML)
                .when().post("/api/v1/services")
                .then().statusCode(202)
                .extract().header("Location");

        given()
                .when().get(location)
                .then().statusCode(200)
                .body("phase", is("DECLARED"))
                .body("observedGeneration", is(1));
    }

    @Test
    void statusForAnUndeclaredServiceReturns404() {
        given()
                .when().get("/api/v1/services/never-declared-service/status")
                .then().statusCode(404);
    }

    @Test
    void stopActionOnAnUndeclaredServiceReturns404() {
        given()
                .when().post("/api/v1/services/never-declared-service/actions/stop")
                .then().statusCode(404);
    }

    @Test
    void discoverOnAServiceWithNoRegisteredInstanceReturnsAnEmptyList() {
        given()
                .when().get("/api/v1/discover/never-registered-service")
                .then().statusCode(200)
                .body("serviceId", is("never-registered-service"))
                .body("instances.size()", is(0));
    }
}
