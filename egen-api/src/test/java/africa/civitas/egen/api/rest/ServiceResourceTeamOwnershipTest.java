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
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Régression de sécurité (docs/architecture/14-securite.md, « refus par défaut ») : redéclarer un service qui
 * existe déjà exige d'en être propriétaire. Avant la correction, {@code Declare} ne comparait le jeton qu'à
 * l'équipe écrite dans le NOUVEAU manifeste : l'équipe B pouvait réécrire le service de l'équipe A (image,
 * réplicas…) et s'en attribuer la propriété en y écrivant {@code team: team-b}.
 *
 * <p>Les jetons portent un vrai claim {@code team} ({@code @JwtSecurity}) ; aucun rôle administrateur, sauf
 * dans le dernier test. Chaque test crée son propre service : le registre en mémoire est partagé.</p>
 */
@QuarkusTest
class ServiceResourceTeamOwnershipTest {

    @Inject
    RegistryStorePort registry;

    @BeforeAll
    static void configureYamlEncoding() {
        RestAssured.config = RestAssured.config()
                .encoderConfig(EncoderConfig.encoderConfig()
                        .encodeContentTypeAs("application/yaml", ContentType.TEXT));
    }

    private String serviceOwnedBy(String team) {
        String name = "it-own-" + UUID.randomUUID().toString().substring(0, 8);
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

    @Test
    @TestSecurity(user = "bob", roles = "developer")
    @JwtSecurity(claims = @Claim(key = "team", value = "team-b"))
    void anotherTeamCannotRedeclareAnExistingServiceByWritingItsOwnTeamInTheManifest() {
        String name = serviceOwnedBy("team-a");
        // Le registre attribue lui-même la génération à l'enregistrement (la valeur passée à save() est ignorée) :
        // on compare donc à la génération observée AVANT la tentative, pas à une constante.
        long generationBefore = registry.findById(ServiceId.of(name)).orElseThrow().generation();

        assertEquals(403, declare(name, "team-b", "attacker/image:9.9.9"));

        DesiredState unchanged = registry.findById(ServiceId.of(name)).orElseThrow();
        assertEquals("team-a", unchanged.manifest().ownerTeam().value(), "la propriété ne doit pas changer de main");
        assertEquals(generationBefore, unchanged.generation(), "aucune nouvelle génération ne doit avoir été enregistrée");
        assertEquals("registry.civitas.africa/" + name + ":1.0.0", unchanged.manifest().deployment().image());
    }

    @Test
    @TestSecurity(user = "alice", roles = "developer")
    @JwtSecurity(claims = @Claim(key = "team", value = "team-a"))
    void theOwnerTeamCanStillRedeclareItsOwnService() {
        String name = serviceOwnedBy("team-a");

        assertEquals(202, declare(name, "team-a", "registry.civitas.africa/" + name + ":2.0.0"));
    }

    @Test
    @TestSecurity(user = "bob", roles = "developer")
    @JwtSecurity(claims = @Claim(key = "team", value = "team-b"))
    void aTeamCanStillDeclareABrandNewServiceUnderItsOwnTeam() {
        String name = "it-new-" + UUID.randomUUID().toString().substring(0, 8);

        assertEquals(202, declare(name, "team-b", "registry.civitas.africa/" + name + ":1.0.0"));
    }

    @Test
    @TestSecurity(user = "root", roles = "egen-admin")
    void anAdministratorCanRedeclareAnyServiceOfAnyTeam() {
        String name = serviceOwnedBy("team-a");

        assertEquals(202, declare(name, "team-a", "registry.civitas.africa/" + name + ":3.0.0"));
    }
}
