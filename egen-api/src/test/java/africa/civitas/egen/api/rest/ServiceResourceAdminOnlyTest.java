package africa.civitas.egen.api.rest;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
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
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regle d'autorisation de l'API de controle (docs/architecture/14-securite.md et 13, « Qui peut appeler quoi » ;
 * decision de Samuel du 10 novembre 2026) : <b>lire</b> est ouvert a tout appelant authentifie, <b>modifier</b>
 * exige le role administrateur, et {@code metadata.team} est purement informatif — il n'est jamais lu pour decider.
 *
 * <p>Remplace {@code ServiceResourceTeamOwnershipTest} (autorisation par equipe, supprimee). Les jetons « developer »
 * portent parfois un claim {@code team} ({@code @JwtSecurity}) : ce claim ne doit donner AUCUN droit. Chaque test
 * cree son propre service : le registre en memoire est partage.</p>
 */
@QuarkusTest
class ServiceResourceAdminOnlyTest {

    @Inject
    RegistryStorePort registry;

    @BeforeAll
    static void configureYamlEncoding() {
        RestAssured.config = RestAssured.config()
                .encoderConfig(EncoderConfig.encoderConfig()
                        .encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    private String serviceOwnedBy(String team) {
        String name = "it-adm-" + UUID.randomUUID().toString().substring(0, 8);
        ServiceId id = ServiceId.of(name);
        ServiceManifest manifest = new ServiceManifest(id, ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry.civitas.africa/" + name + ":1.0.0", "java"),
                new DeploymentSpec("nomad", "registry.civitas.africa/" + name + ":1.0.0", "500m", "512Mi",
                        new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(Duration.ofMillis(50)), List.of(), OwnerTeam.of(team));
        registry.save(new DesiredState(manifest, 0L, TargetEnvironment.of("development")));
        return name;
    }

    private static String manifest(String name, String team, String image) {
        return """
                apiVersion: egen.civitas.africa/v1
                kind: ServiceManifest
                metadata:
                  name: %s
                  version: 1.0.0
                  team: %s
                runtime:
                  type: container
                  artifact: %s
                deployment:
                  adapter: nomad
                  image: %s
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
                """.formatted(name, team, image, image);
    }

    private static int declare(String name, String team, String image) {
        return given()
                .contentType("application/yaml")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(manifest(name, team, image))
                .when().post("/api/v1/services")
                .then().extract().statusCode();
    }

    // ------------------------------------------------------------------ modifier : administrateur seulement

    @Test
    @TestSecurity(user = "alice", roles = "developer")
    @JwtSecurity(claims = @Claim(key = "team", value = "team-a"))
    void aCallerWithoutTheAdministratorRoleCannotDeclareEvenWithATeamClaimEqualToTheManifestTeam() {
        String name = "it-new-" + UUID.randomUUID().toString().substring(0, 8);

        assertEquals(403, declare(name, "team-a", "registry.civitas.africa/" + name + ":1.0.0"));
        assertTrue(registry.findById(ServiceId.of(name)).isEmpty(), "rien ne doit avoir ete enregistre");
    }

    @Test
    @TestSecurity(user = "bob", roles = "developer")
    @JwtSecurity(claims = @Claim(key = "team", value = "team-a"))
    void aCallerWithoutTheAdministratorRoleCannotRedeclareAnExistingServiceEvenOfItsOwnTeam() {
        String name = serviceOwnedBy("team-a");
        // Le registre attribue lui-meme la generation a l'enregistrement : on compare a la generation observee
        // AVANT la tentative, pas a une constante.
        long generationBefore = registry.findById(ServiceId.of(name)).orElseThrow().generation();

        assertEquals(403, declare(name, "team-a", "attacker/image:9.9.9"));

        DesiredState unchanged = registry.findById(ServiceId.of(name)).orElseThrow();
        assertEquals(generationBefore, unchanged.generation(), "aucune nouvelle generation ne doit avoir ete enregistree");
        assertEquals("registry.civitas.africa/" + name + ":1.0.0", unchanged.manifest().deployment().image());
    }

    @Test
    @TestSecurity(user = "bob", roles = "developer")
    void theAdministratorCheckComesBeforeTheYamlIsRead() {
        // YAML volontairement illisible : un 403 (et non un 400) prouve que le droit passe AVANT la lecture du corps.
        given().contentType("application/yaml")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body("pas: [du yaml")
                .when().post("/api/v1/services")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "bob", roles = "developer")
    void aCallerWithoutTheAdministratorRoleCannotStopAnyServiceKnownOrUnknown() {
        String name = serviceOwnedBy("team-a");

        given().header("Idempotency-Key", UUID.randomUUID().toString())
                .when().post("/api/v1/services/" + name + "/actions/stop").then().statusCode(403);
        // Un identifiant inconnu est aussi 403 (jamais 404) : un non-administrateur n'apprend pas quels services existent.
        given().header("Idempotency-Key", UUID.randomUUID().toString())
                .when().post("/api/v1/services/it-never-declared/actions/stop").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "root", roles = "egen-admin")
    void anAdministratorCanDeclareAnyServiceWhateverTeamIsWrittenAndTheTeamIsReadBackUnchanged() {
        String name = serviceOwnedBy("team-a");

        // Un autre texte dans metadata.team ne change rien au droit : modifier ne depend que du role.
        assertEquals(202, declare(name, "team-zzz", "registry.civitas.africa/" + name + ":2.0.0"));

        given().when().get("/api/v1/services/" + name)
                .then().statusCode(200)
                .body("manifest.metadata.team", is("team-zzz"));
    }

    // ------------------------------------------------------------------ lire : tout appelant authentifie

    @Test
    @TestSecurity(user = "bob", roles = "developer")
    @JwtSecurity(claims = @Claim(key = "team", value = "team-b"))
    void anyAuthenticatedCallerCanReadTheManifestOfAServiceDeclaredWithAnotherTeam() {
        String name = serviceOwnedBy("team-a");

        given().when().get("/api/v1/services/" + name)
                .then().statusCode(200)
                .body("serviceId", is(name))
                .body("manifest.metadata.team", is("team-a"));
    }

    @Test
    @TestSecurity(user = "bob", roles = "developer")
    @JwtSecurity(claims = @Claim(key = "team", value = "team-b"))
    void theListContainsTheServicesOfEveryTeamForAnyAuthenticatedCaller() {
        String ofTeamA = serviceOwnedBy("team-a");
        String ofTeamB = serviceOwnedBy("team-b");

        given().when().get("/api/v1/services")
                .then().statusCode(200)
                .body("serviceId", hasItem(ofTeamA))
                .body("serviceId", hasItem(ofTeamB));
    }

    @Test
    @TestSecurity(user = "bob", roles = "developer")
    void anUndeclaredServiceIsStill404ForReading() {
        given().when().get("/api/v1/services/it-never-declared").then().statusCode(404);
    }
}
