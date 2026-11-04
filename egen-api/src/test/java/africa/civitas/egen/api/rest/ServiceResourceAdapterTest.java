package africa.civitas.egen.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Niveau 2 (docs/architecture/17) : ce que l'appelant HTTP voit quand {@code deployment.adapter} est
 * validé à {@code Declare} (docs/architecture/06, validation, étape 3 ; docs/architecture/07,
 * « Sélection et routage »). Câblage de {@link TestUseCaseProducers} : {@code nomad} et
 * {@code kubernetes} sont activés, rien d'autre.
 *
 * <p>Chaque test déclare un service au nom unique : le registre en mémoire est partagé par toutes les
 * classes de test de ce module.</p>
 */
@QuarkusTest
@TestSecurity(user = "test-admin", roles = "egen-admin")
class ServiceResourceAdapterTest {

    @BeforeAll
    static void configureYamlEncoding() {
        RestAssured.config = RestAssured.config()
                .encoderConfig(EncoderConfig.encoderConfig()
                        .encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    private static String uniqueName() {
        return "adapter-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String manifest(String name, String adapter) {
        return """
                apiVersion: egen.civitas.africa/v1
                kind: ServiceManifest
                metadata:
                  name: %s
                  version: 1.0.0
                runtime:
                  type: container
                  artifact: busybox:latest
                deployment:
                  adapter: %s
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
                """.formatted(name, adapter);
    }

    /** Une valeur fraiche a chaque appel — voir docs/architecture/13-api-et-contrats.md, "Idempotency-Key". */
    private static ValidatableResponse declare(String name, String adapter) {
        return given()
                .contentType("application/yaml")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(manifest(name, adapter))
                .when().post("/api/v1/services")
                .then();
    }

    @Test
    void aDeclareOnAnEnabledAdapterIsAccepted() {
        declare(uniqueName(), "nomad").statusCode(202);
        declare(uniqueName(), "kubernetes").statusCode(202);
    }

    @Test
    void aDeclareOnAnAdapterThatIsNotEnabledIsRejectedWith400AndLeavesNoTrace() {
        String name = uniqueName();

        declare(name, "docker").statusCode(400);

        given().when().get("/api/v1/services/" + name + "/status").then().statusCode(404);
    }

    @Test
    void changingTheAdapterOfAnExistingServiceIsRejectedWith409() {
        String name = uniqueName();
        declare(name, "nomad").statusCode(202);

        declare(name, "kubernetes").statusCode(409);

        // L'adapter d'origine reste valide : redéclarer sur le même adapter est accepté.
        declare(name, "nomad").statusCode(202);
    }
}
