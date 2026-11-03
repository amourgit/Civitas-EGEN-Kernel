package africa.civitas.egen.api.rest;

import africa.civitas.egen.testkit.InMemoryConfigurationPort;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrat de {@code PUT/GET /api/v1/services/{id}/configuration} (docs/architecture/13-api-et-contrats.md,
 * « Publication de configuration », evolution A3) : 202, 400 (toutes les violations, jamais une valeur), 403
 * avant lecture du corps, 404, 503, idempotence, remplacement complet, environnements.
 */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = "egen-admin")
class ServiceConfigurationResourceTest {

    @Inject
    InMemoryConfigurationPort store;

    @BeforeAll
    static void yamlEncoding() {
        RestAssured.config = RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig().encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    @AfterEach
    void storeIsBackUp() {
        store.failing(false);
    }

    private static String uniqueName() {
        return "it-cfg-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Service qui attend db.host (sans defaut), db.port (entier, sans defaut), cache.enabled (booleen, defaut) et un secret. */
    private static String declare(String name) {
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
                configuration:
                  required:
                    - { key: db.host, type: string }
                    - { key: db.port, type: integer }
                    - { key: cache.enabled, type: boolean, default: "true" }
                  secretsRefs:
                    - { key: db.password, provider: vault, path: secret/news/db }
                """.formatted(name, name);
        given().header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/yaml")
                .body(yaml).when().post("/api/v1/services").then().statusCode(202);
        return name;
    }

    private static RequestSpecification put(String body) {
        return given().header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(ContentType.JSON).body(body);
    }

    private static final String VALID = "{ \"values\": { \"db.host\": \"pg.internal\", \"db.port\": 5432 } }";

    // ------------------------------------------------------------------ ecriture

    @Test
    void aValidPublicationIs202WithLocationAndVersion() {
        String name = declare(uniqueName());

        put(VALID).when().put("/api/v1/services/" + name + "/configuration")
                .then().statusCode(202)
                .header("Location", endsWith("/api/v1/services/" + name + "/configuration?environment=development"))
                .body("serviceId", is(name))
                .body("environment", is("development"))
                .body("version", is(1))
                .body("configurationUrl", endsWith("/configuration?environment=development"));

        given().when().get("/api/v1/services/" + name + "/configuration")
                .then().statusCode(200)
                .body("serviceId", is(name))
                .body("environment", is("development"))
                .body("version", is(1))
                .body("values.'db.host'", is("pg.internal"))
                .body("values.'db.port'", is("5432"));
    }

    @Test
    void scalarsAreConvertedToText() {
        String name = declare(uniqueName());

        put("{ \"values\": { \"db.host\": \"h\", \"db.port\": 5432, \"cache.enabled\": false } }")
                .when().put("/api/v1/services/" + name + "/configuration").then().statusCode(202);

        given().when().get("/api/v1/services/" + name + "/configuration").then().statusCode(200)
                .body("values.'db.port'", is("5432"))
                .body("values.'cache.enabled'", is("false"));
    }

    @Test
    void aSecondPublicationReplacesTheWholeSetAndBumpsTheVersion() {
        String name = declare(uniqueName());
        put("{ \"values\": { \"db.host\": \"a\", \"db.port\": 1, \"cache.enabled\": false } }")
                .when().put("/api/v1/services/" + name + "/configuration").then().statusCode(202);

        put("{ \"values\": { \"db.host\": \"b\", \"db.port\": 2 } }")
                .when().put("/api/v1/services/" + name + "/configuration").then().statusCode(202).body("version", is(2));

        Map<String, String> values = given().when().get("/api/v1/services/" + name + "/configuration").then()
                .statusCode(200).body("version", is(2)).body("values.'db.host'", is("b"))
                .extract().jsonPath().getMap("values");
        assertFalse(values.containsKey("cache.enabled"), "son defaut du manifeste s'applique : " + values);
    }

    @Test
    void environmentsAreIndependentAndTheDefaultIsTheKernelEnvironment() {
        String name = declare(uniqueName());
        put("{ \"values\": { \"db.host\": \"prod\", \"db.port\": 1 } }")
                .when().put("/api/v1/services/" + name + "/configuration?environment=production").then().statusCode(202)
                .body("environment", is("production"));
        put("{ \"values\": { \"db.host\": \"dev\", \"db.port\": 2 } }")
                .when().put("/api/v1/services/" + name + "/configuration").then().statusCode(202);

        given().when().get("/api/v1/services/" + name + "/configuration?environment=production").then()
                .body("values.'db.host'", is("prod"));
        given().when().get("/api/v1/services/" + name + "/configuration").then()
                .body("environment", is("development")).body("values.'db.host'", is("dev"));
    }

    @Test
    void aNeverPublishedConfigurationIsEmptyWithVersionZero() {
        String name = declare(uniqueName());

        Map<String, String> values = given().when().get("/api/v1/services/" + name + "/configuration").then()
                .statusCode(200).body("version", is(0)).extract().jsonPath().getMap("values");
        assertTrue(values.isEmpty());
    }

    // ------------------------------------------------------------------ validation

    @Test
    void everyViolationIsReturnedTogetherAndNeverTheRefusedValue() {
        String name = declare(uniqueName());

        Response response = put("""
                { "values": { "db.port": "VALEUR-SECRETE-1", "db.password": "VALEUR-SECRETE-2", "inconnue": "x" } }
                """).when().put("/api/v1/services/" + name + "/configuration").then().statusCode(400)
                .body("violations", hasSize(4))
                .body("violations.key", containsInAnyOrder("db.host", "db.password", "db.port", "inconnue"))
                .body("violations.find { it.key == 'db.host' }.rule", is("MISSING_REQUIRED_KEY"))
                .body("violations.find { it.key == 'db.password' }.rule", is("SECRET_NOT_PUBLISHABLE"))
                .body("violations.find { it.key == 'db.port' }.rule", is("TYPE_MISMATCH"))
                .body("violations.find { it.key == 'inconnue' }.rule", is("UNKNOWN_KEY"))
                .extract().response();

        assertFalse(response.asString().contains("VALEUR-SECRETE"), "jamais une valeur : " + response.asString());
        given().when().get("/api/v1/services/" + name + "/configuration").then()
                .body("version", is(0)); // rien n'a ete publie
    }

    @Test
    void bodiesOutsideTheContractAre400() {
        String name = declare(uniqueName());
        String url = "/api/v1/services/" + name + "/configuration";

        put("pas du json").when().put(url).then().statusCode(400);
        put("[]").when().put(url).then().statusCode(400);
        put("{}").when().put(url).then().statusCode(400);
        put("{ \"values\": [1] }").when().put(url).then().statusCode(400);
        put("{ \"values\": { \"db.host\": { \"a\": 1 } } }").when().put(url).then().statusCode(400)
                .body("violations[0].key", is("db.host"));
        put("{ \"values\": { \"db.host\": [1] } }").when().put(url).then().statusCode(400);
        put("{ \"values\": { \"db.host\": null } }").when().put(url).then().statusCode(400);
        put("{ \"values\": {}, \"extra\": 1 }").when().put(url).then().statusCode(400);
        put("{ \"values\": { \"db.host\": \"a\", \"db.host\": \"b\" } }").when().put(url).then().statusCode(400);

        given().when().get(url).then().body("version", is(0));
    }

    // ------------------------------------------------------------------ erreurs et idempotence

    @Test
    void anUnknownServiceIs404ForBothVerbs() {
        put(VALID).when().put("/api/v1/services/it-cfg-inconnu/configuration").then().statusCode(404);
        given().when().get("/api/v1/services/it-cfg-inconnu/configuration").then().statusCode(404);
    }

    @Test
    void theIdempotencyKeyIsRequiredOnWritesAndReplayedWhenRepeated() {
        String name = declare(uniqueName());
        String url = "/api/v1/services/" + name + "/configuration";

        given().contentType(ContentType.JSON).body(VALID).when().put(url).then().statusCode(400);

        String key = UUID.randomUUID().toString();
        given().header("Idempotency-Key", key).contentType(ContentType.JSON).body(VALID)
                .when().put(url).then().statusCode(202).body("version", is(1));
        given().header("Idempotency-Key", key).contentType(ContentType.JSON).body(VALID)
                .when().put(url).then().statusCode(202).body("version", is(1))
                .header("Idempotency-Replayed", is("true"));

        given().when().get(url).then().body("version", is(1)); // un seul ecrit
    }

    @Test
    void anUnavailableStoreIs503AndNothingIsPublished() {
        String name = declare(uniqueName());
        String url = "/api/v1/services/" + name + "/configuration";
        store.failing(true);

        put(VALID).when().put(url).then().statusCode(503);
        given().when().get(url).then().statusCode(503);

        store.failing(false);
        given().when().get(url).then().statusCode(200).body("version", is(0));
    }
}
