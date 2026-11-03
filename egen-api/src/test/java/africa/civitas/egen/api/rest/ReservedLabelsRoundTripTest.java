package africa.civitas.egen.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evolution <b>A2</b> (docs 19 ; 06, « Labels reserves poses par la plateforme » ; 22.4) : le prefixe
 * {@code platform.civitas.africa/} est reserve a la Plateforme ; le Kernel stocke et restitue ces labels
 * <b>comme n'importe quel autre, sans jamais s'en servir</b> (garde-fou n°1). Aucun code ne les connait : ce
 * test d'aller-retour de l'API est la seule preuve attendue.
 */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = "egen-admin")
class ReservedLabelsRoundTripTest {

    private static final String P = "platform.civitas.africa/";

    @BeforeAll
    static void yamlEncoding() {
        RestAssured.config = RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    private static String uniqueName() {
        return "it-labels-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String manifest(String name, String ownerCell, String configVersion) {
        return """
                apiVersion: egen.civitas.africa/v1
                kind: ServiceManifest
                metadata:
                  name: %s
                  version: 1.0.0
                  team: platform-operator
                  labels:
                    "platform.civitas.africa/instance-id": "inst-42"
                    "platform.civitas.africa/domain": "calendar"
                    "platform.civitas.africa/implementation": "radicale"
                    "platform.civitas.africa/implementation-version": "3.2.1"
                    "platform.civitas.africa/owner-cell": "%s"
                    "equipe.exemple/libre": "valeur ordinaire"
                  annotations:
                    "platform.civitas.africa/config-version": "%s"
                runtime:
                  type: container
                  language: python
                deployment:
                  adapter: nomad
                  image: registry.civitas.africa/radicale:3.2.1
                  resources: { cpu: 250m, memory: 256Mi }
                  replicas: { min: 1, max: 1 }
                health:
                  http: { endpoint: /.web/, interval: 10s, timeout: 2s, failuresBeforeUnhealthy: 3 }
                """.formatted(name, ownerCell, configVersion);
    }

    private static void declare(String yaml, int expectedStatus) {
        given().header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/yaml")
                .body(yaml).when().post("/api/v1/services").then().statusCode(expectedStatus);
    }

    @Test
    void reservedLabelsAndAnnotationsAreReadBackIntact() {
        String name = uniqueName();
        declare(manifest(name, "cellule-opaque-7f3a", "12"), 202);

        var manifest = given().when().get("/api/v1/services/" + name).then().statusCode(200)
                .body("generation", is(1)).body("manifest.metadata.team", is("platform-operator"))
                .extract().jsonPath();

        assertEquals(Map.of(
                        P + "instance-id", "inst-42",
                        P + "domain", "calendar",
                        P + "implementation", "radicale",
                        P + "implementation-version", "3.2.1",
                        P + "owner-cell", "cellule-opaque-7f3a",
                        "equipe.exemple/libre", "valeur ordinaire"),
                manifest.getMap("manifest.metadata.labels"));
        assertEquals(Map.of(P + "config-version", "12"), manifest.getMap("manifest.metadata.annotations"));
    }

    @Test
    void theListEndpointReturnsThemToo() {
        String name = uniqueName();
        declare(manifest(name, "cellule-opaque-1", "1"), 202);

        List<Map<String, Object>> services = given().when().get("/api/v1/services").then().statusCode(200)
                .extract().jsonPath().getList("$");
        Map<String, Object> mine = services.stream().filter(s -> name.equals(s.get("serviceId"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) ((Map<String, Object>) mine.get("manifest")).get("metadata");
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) metadata.get("labels");

        assertEquals("inst-42", labels.get(P + "instance-id"));
    }

    @Test
    void aRedeclarationReplacesThemAndTheKernelNeverInterpretsTheirValues() {
        String name = uniqueName();
        declare(manifest(name, "cellule-a", "1"), 202);

        // Une valeur quelconque, meme etrange : le Kernel ne la valide ni ne la lit — l'identifiant de cellule est opaque.
        declare(manifest(name, "n'importe quoi : #1 @ ?", "2"), 202);

        var read = given().when().get("/api/v1/services/" + name).then().statusCode(200)
                .body("generation", is(2)).extract().jsonPath();
        assertEquals("n'importe quoi : #1 @ ?", read.getMap("manifest.metadata.labels").get(P + "owner-cell"));
        assertEquals("2", read.getMap("manifest.metadata.annotations").get(P + "config-version"));
        assertTrue(read.getMap("manifest.metadata.labels").containsKey(P + "instance-id"));
    }
}
