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
 * Securite de {@code /api/v1/services/{id}/configuration} (docs 13 A3, 14 ; decision du 10 novembre 2026) : la
 * <b>publication</b> ({@code PUT}) est une modification, reservee aux administrateurs, et le droit est verifie
 * <b>avant toute lecture du corps</b> et avant de chercher le service ; la <b>lecture</b> ({@code GET}) est ouverte
 * a tout appelant authentifie. {@code metadata.team} est informatif : il ne donne aucun droit.
 *
 * <p>Remplace {@code ServiceConfigurationSecurityTest} (autorisation par equipe, supprimee). Le service est inscrit
 * directement dans le Registry.</p>
 */
@QuarkusTest
@TestSecurity(user = "test-dev", roles = "developer")
class ServiceConfigurationAdminOnlyTest {

    @Inject
    RegistryStorePort registry;

    private String serviceOfTeam(String team) {
        String name = "it-cfg-adm-" + UUID.randomUUID().toString().substring(0, 8);
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
    void aCallerWithoutTheAdministratorRoleCannotPublishAndTheBodyIsNeverRead() {
        String name = serviceOfTeam("content-platform");

        // Corps volontairement illisible : un 403 (et non un 400) prouve que le droit passe AVANT la lecture.
        given().header("Idempotency-Key", UUID.randomUUID().toString()).contentType(ContentType.JSON)
                .body("pas du json")
                .when().put("/api/v1/services/" + name + "/configuration").then().statusCode(403);
    }

    @Test
    void publishingOnAnUnknownServiceIs403ForAnyCallerWithoutTheAdministratorRole() {
        // Un identifiant inconnu est 403 (jamais 404) pour un non-administrateur : il n'apprend pas quels services existent.
        given().header("Idempotency-Key", UUID.randomUUID().toString()).contentType(ContentType.JSON)
                .body("{}")
                .when().put("/api/v1/services/it-never-declared/configuration").then().statusCode(403);
    }

    @Test
    void anyAuthenticatedCallerCanReadTheConfigurationWhateverTheTeamOfTheService() {
        given().when().get("/api/v1/services/" + serviceOfTeam("content-platform") + "/configuration")
                .then().statusCode(200);
        given().when().get("/api/v1/services/" + serviceOfTeam(null) + "/configuration")
                .then().statusCode(200);
    }

    @Test
    void readingTheConfigurationOfAnUndeclaredServiceIs404() {
        given().when().get("/api/v1/services/it-never-declared/configuration").then().statusCode(404);
    }
}
