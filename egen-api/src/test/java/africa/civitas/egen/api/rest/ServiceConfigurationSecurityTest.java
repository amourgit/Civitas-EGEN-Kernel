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
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Securite de {@code /api/v1/services/{id}/configuration} (docs 13 A3, 14) : un appelant qui n'est ni l'equipe
 * proprietaire ni administrateur recoit 403 — et le droit est verifie <b>avant toute lecture du corps</b>.
 * Le service est inscrit directement dans le Registry : declarer exige elle-meme les droits de l'equipe.
 */
@QuarkusTest
@TestSecurity(user = "test-dev", roles = "developer")
class ServiceConfigurationSecurityTest {

    @Inject
    RegistryStorePort registry;

    private String serviceOwnedBy(String team) {
        String name = "it-cfg-sec-" + UUID.randomUUID().toString().substring(0, 8);
        ServiceId id = ServiceId.of(name);
        ServiceManifest manifest = new ServiceManifest(id, ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry.civitas.africa/" + name + ":1.0.0", "java"),
                new DeploymentSpec("nomad", "registry.civitas.africa/" + name + ":1.0.0", "500m", "512Mi",
                        new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(Duration.ofMillis(50)), List.of(),
                team == null ? OwnerTeam.unassigned() : OwnerTeam.of(team));
        registry.save(new DesiredState(manifest, 0L, TargetEnvironment.of("development")));
        return name;
    }

    @Test
    void aCallerOutsideTheOwnerTeamIs403OnBothVerbsAndTheBodyIsNeverRead() {
        String name = serviceOwnedBy("content-platform");

        // Corps volontairement illisible : un 403 (et non un 400) prouve que le droit passe AVANT la lecture.
        given().header("Idempotency-Key", UUID.randomUUID().toString()).contentType(ContentType.JSON)
                .body("pas du json")
                .when().put("/api/v1/services/" + name + "/configuration").then().statusCode(403);
        given().when().get("/api/v1/services/" + name + "/configuration").then().statusCode(403);
    }

    @Test
    void aServiceWithoutAnAssignedTeamStaysOpenToAnyAuthenticatedCaller() {
        // Choix de transition documente (docs 14, note de migration) : un service historique sans equipe n'est
        // jamais bloque retroactivement.
        String name = serviceOwnedBy(null);

        given().when().get("/api/v1/services/" + name + "/configuration").then().statusCode(200);
    }
}
