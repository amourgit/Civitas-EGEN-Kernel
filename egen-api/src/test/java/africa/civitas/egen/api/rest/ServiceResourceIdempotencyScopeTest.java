package africa.civitas.egen.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Régression de sécurité (docs/architecture/14-securite.md, « zero trust ») : la clé d'idempotence est propre à
 * l'appelant. Avant la correction, deux appelants authentifiés qui envoyaient la même valeur d'en-tête
 * {@code Idempotency-Key} sur le même chemin partageaient une entrée de cache : le second recevait la
 * réponse du premier (donc un 202 trompeur) et sa propre requête n'était <b>jamais exécutée</b>.
 *
 * <p>Le cache est partagé par toute l'application de test : les deux tests, ordonnés, s'appuient dessus. Noms de
 * service uniques par exécution : le registre en mémoire est partagé par toutes les classes de test.</p>
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServiceResourceIdempotencyScopeTest {

    private static final String SHARED_KEY = "cle-choisie-par-les-clients-" + UUID.randomUUID();
    private static final String ALICE_SERVICE = "idem-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String BOB_SERVICE = "idem-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    static void configureYamlEncoding() {
        RestAssured.config = RestAssured.config()
                .encoderConfig(EncoderConfig.encoderConfig()
                        .encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    private static String manifest(String name) {
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
                """.formatted(name);
    }

    private static void declare(String serviceName, String idempotencyKey, int expectedStatus) {
        given()
                .contentType("application/yaml")
                .header("Idempotency-Key", idempotencyKey)
                .body(manifest(serviceName))
                .when().post("/api/v1/services")
                .then().statusCode(expectedStatus);
    }

    @Test
    @Order(1)
    @TestSecurity(user = "alice", roles = "egen-admin")
    void aliceDeclaresHerServiceWithAKeyOfHerChoice() {
        declare(ALICE_SERVICE, SHARED_KEY, 202);
    }

    @Test
    @Order(2)
    @TestSecurity(user = "bob", roles = "egen-admin")
    void bobUsingTheSameKeyOnTheSamePathStillGetsHisOwnRequestExecuted() {
        declare(BOB_SERVICE, SHARED_KEY, 202);

        // Avec le défaut, ce 202 aurait été la réponse d'Alice rejouée : le service de Bob n'existerait pas.
        given().when().get("/api/v1/services/" + BOB_SERVICE + "/status").then().statusCode(200);
    }

    @Test
    @Order(3)
    @TestSecurity(user = "alice", roles = "egen-admin")
    void aliceReplayingHerOwnKeyStillGetsTheCachedResponseNotASecondExecution() {
        // L'idempotence elle-même reste intacte pour un même appelant : la répétition renvoie la réponse d'origine.
        declare(ALICE_SERVICE, SHARED_KEY, 202);
    }
}
