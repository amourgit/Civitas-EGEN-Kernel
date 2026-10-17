package africa.civitas.egen.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Lecture du manifeste declare — {@code GET /api/v1/services[/{id}]} (docs/architecture/13) : ce qui a ete
 * declare, y compris le bloc {@code interfaces} opaque (docs/architecture/21, 21.3), est restitue tel quel.
 */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = "egen-admin")
class ServiceManifestReadResourceTest {

    @BeforeAll
    static void yamlEncoding() {
        RestAssured.config = RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    private static String manifest(String name) {
        return """
                apiVersion: egen.civitas.africa/v1
                kind: ServiceManifest
                metadata:
                  name: %s
                  version: 2.4.0
                  team: content-platform
                  labels: { domain: news }
                runtime:
                  type: container
                  language: java
                deployment:
                  adapter: nomad
                  image: registry.civitas.africa/news:2.4.0
                  resources: { cpu: 500m, memory: 512Mi }
                  replicas: { min: 1, max: 2 }
                health:
                  http: { endpoint: /health, interval: 10s, timeout: 2s, failuresBeforeUnhealthy: 3 }
                interfaces:
                  - { type: openapi, ref: /internal/openapi.json, riskDefault: read }
                  - { type: grpc, ref: "grpc://news:9090", riskDefault: catastrophic }
                """.formatted(name);
    }

    private static void declare(String name) {
        given().header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/yaml")
                .body(manifest(name)).when().post("/api/v1/services").then().statusCode(202);
    }

    @Test
    void aDeclaredManifestIsReadBackWithItsInterfacesUntouched() {
        String name = "it-read-" + UUID.randomUUID().toString().substring(0, 8);
        declare(name);

        given().when().get("/api/v1/services/" + name)
                .then().statusCode(200)
                .body("serviceId", is(name))
                .body("generation", is(1))
                .body("manifest.metadata.name", is(name))
                .body("manifest.metadata.team", is("content-platform"))
                .body("manifest.metadata.labels.domain", is("news"))
                .body("manifest.interfaces", hasSize(2))
                .body("manifest.interfaces[0].type", is("openapi"))
                .body("manifest.interfaces[0].ref", is("/internal/openapi.json"))
                .body("manifest.interfaces[1].type", is("grpc"))
                .body("manifest.interfaces[1].riskDefault", is("catastrophic"))
                .body("manifest.events", nullValue())
                .body("manifest.resiliency.timeout", is("30000ms"));
    }

    @Test
    void anUndeclaredServiceIs404AndAMalformedIdIs400() {
        given().when().get("/api/v1/services/it-never-declared").then().statusCode(404);
        given().when().get("/api/v1/services/Pas Un Id").then().statusCode(400);
    }

    @Test
    void theListContainsEveryReadableDeclaredService() {
        String name = "it-list-" + UUID.randomUUID().toString().substring(0, 8);
        declare(name);

        given().when().get("/api/v1/services")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("serviceId", hasItem(name))
                .body("[0].manifest", notNullValue());
    }
}
