package africa.civitas.egen.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Verifie le cycle "Declare" -&gt; "Observe" complet au niveau HTTP (voir
 * docs/architecture/20-scenario-bout-en-bout.md, etapes 1 et 8), avec le
 * cablage CDI de test de {@link TestUseCaseProducers} (RegistryStorePort en
 * memoire, aucun Nomad reel requis a ce niveau).
 *
 * <p><b>Content-type "application/yaml"</b> : REST Assured n'a pas
 * d'encodeur connu pour ce content-type (seuls TEXT/JSON/XML/BINARY le sont
 * nativement) — sans configuration, il tente de serialiser le corps comme un
 * objet au lieu d'envoyer la chaine YAML telle quelle, et echoue avec
 * "Don't know how to encode ... as a byte stream". {@link #configureYamlEncoding()}
 * applique la config que REST Assured recommande lui-meme dans ce message
 * d'erreur : traiter "application/yaml" comme du texte brut. Ceci ne concerne
 * que le client de test — {@link ServiceResource} lit deja le corps de la
 * requete comme du texte brut cote serveur (voir son {@code @Consumes}), rien
 * n'y a change.</p>
 *
 * <p><b>Portee de "Observe" ici</b> : {@link TestUseCaseProducers} ne cable
 * volontairement qu'un {@code RegistryStorePort} en memoire et une
 * {@code WorkQueue} — jamais de {@code ReconciliationEngine} (celui-ci vit
 * dans egen-bootstrap, seul module autorise a tout connaitre, voir
 * docs/architecture/16-packages-et-stack-technique.md). Un GET juste apres
 * un POST ne peut donc jamais refleter une reconciliation qui n'existe pas
 * dans ce cablage : il lit {@link africa.civitas.egen.domain.lifecycle.ServiceStatus#initial()}
 * (phase {@code DECLARED}, {@code observedGeneration=0}), pas le resultat
 * d'une convergence. Cette classe verifie donc uniquement que "Declare"
 * persiste et repond 202+Location, et que "Observe" retourne un statut par
 * defaut coherent juste apres — pas la convergence complete jusqu'a
 * {@code RUNNING}, qui releve d'un test de niveau 5 (voir
 * docs/architecture/17-strategie-de-tests.md) avec un ReconciliationEngine
 * reellement cable et actif.</p>
 */
@QuarkusTest
class ServiceResourceTest {

    @BeforeAll
    static void configureYamlEncoding() {
        RestAssured.config = RestAssured.config()
                .encoderConfig(EncoderConfig.encoderConfig()
                        .encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

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

        // observedGeneration reste a 0 ici : aucun ReconciliationEngine n'est
        // cable dans TestUseCaseProducers, donc rien n'a encore ete "observe"
        // pour ce service — voir le javadoc de la classe.
        given()
                .when().get(location)
                .then().statusCode(200)
                .body("phase", is("DECLARED"))
                .body("observedGeneration", is(0));
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
