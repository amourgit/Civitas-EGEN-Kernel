package africa.civitas.egen.adapter.nomad;

import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.ServiceId;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Test de niveau 3 (voir docs/architecture/17-strategie-de-tests.md) : demarre
 * un vrai agent Nomad en mode dev via Testcontainers et verifie le mapping de
 * {@link NomadDeploymentAdapter} contre l'API HTTP reelle — pas seulement
 * contre une documentation.
 *
 * <p><b>Perimetre assume</b> : ce test verifie que Nomad ACCEPTE le job
 * traduit (soumission, lecture du statut, idempotence, suppression). Il
 * n'attend pas que l'allocation atteigne l'etat "running", ce qui exigerait
 * que le driver docker de l'agent Nomad conteneurise puisse lui-meme lancer
 * des conteneurs (Docker-in-Docker), une contrainte d'environnement CI qui
 * deborde du perimetre de ce module — cette convergence de bout en bout est
 * couverte par le test de niveau 5 (voir
 * docs/architecture/20-scenario-bout-en-bout.md) dans un environnement
 * dedie.</p>
 */
@Testcontainers
class NomadDeploymentAdapterIT {

    @Container
    static final GenericContainer<?> NOMAD = new GenericContainer<>("hashicorp/nomad:1.8")
            .withExposedPorts(4646)
            .withCommand("agent", "-dev", "-bind=0.0.0.0", "-log-level=WARN")
            .waitingFor(Wait.forHttp("/v1/status/leader").forStatusCode(200))
            .withStartupTimeout(Duration.ofSeconds(60));

    private NomadDeploymentAdapter adapter() {
        URI baseUri = URI.create("http://" + NOMAD.getHost() + ":" + NOMAD.getMappedPort(4646));
        return new NomadDeploymentAdapter(baseUri, null);
    }

    private static ServiceId serviceId() {
        return ServiceId.of("it-fixture-service");
    }

    private static DeploymentSpec aSpec() {
        return new DeploymentSpec("nomad", "busybox:latest", "100m", "64Mi", new ReplicaRange(1, 1));
    }

    @Test
    void createIsIdempotentAndGetStatusReflectsTheDesiredCount() {
        NomadDeploymentAdapter adapter = adapter();
        ServiceId id = serviceId();

        assertDoesNotThrow(() -> adapter.create(id, aSpec()));
        // Idempotence (garde-fou n7, docs/architecture/02-principes-fondamentaux.md) :
        // rejouer create() avec la meme spec ne doit produire aucune erreur.
        assertDoesNotThrow(() -> adapter.create(id, aSpec()));

        DeploymentObservation observation = adapter.getStatus(id);
        assertEquals(1, observation.desiredCount());

        adapter.remove(id);
        DeploymentObservation afterRemoval = adapter.getStatus(id);
        assertEquals(0, afterRemoval.desiredCount());
    }
}
