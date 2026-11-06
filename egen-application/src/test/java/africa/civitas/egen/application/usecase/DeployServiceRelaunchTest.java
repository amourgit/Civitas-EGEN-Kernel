package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.EnabledDeploymentAdapters;
import africa.civitas.egen.application.reconciliation.PendingDeclareTraces;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ConditionStatus;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relance par Declare (docs/architecture/09-cycle-de-vie.md, « Relance par Declare ») : un Declare accepté sur un
 * service FAILED ou STOPPED le remet en DECLARED, Conditions effacées ; toute autre phase reste intacte ; un Declare
 * refusé ne touche à rien.
 */
class DeployServiceRelaunchTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final TargetEnvironment ENV = TargetEnvironment.of("test");

    /** La Condition laissée par un échec d'adapter : elle ne doit pas survivre à une relance. */
    private static final Condition STALE_FAILURE = new Condition("DeploymentReady", ConditionStatus.FALSE,
            "AdapterFailureThresholdExceeded", "Kubernetes a rejete create/update(news-service) : HTTP 404",
            Instant.EPOCH);

    private final InMemoryRegistryStore registry = new InMemoryRegistryStore();
    private final WorkQueue queue = new WorkQueue();
    private final DeployServiceUseCase useCase = new DeployServiceUseCaseImpl(registry, queue,
            new PendingDeclareTraces(), EnabledDeploymentAdapters.of(Set.of("nomad", "kubernetes")));

    private static ServiceManifest manifest(String adapter) {
        return new ServiceManifest(NEWS, ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry/news:1.0.0", "java"),
                new DeploymentSpec(adapter, "registry/news:1.0.0", "500m", "512Mi", new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                LifecyclePolicy.defaultPolicy());
    }

    private void force(Phase phase, long observedGeneration) {
        registry.saveStatus(NEWS, new ServiceStatus(phase, List.of(STALE_FAILURE), observedGeneration));
    }

    private ServiceStatus status() {
        return registry.findStatus(NEWS).orElseThrow();
    }

    @Test
    void aFailedServiceIsRelaunchedFromDeclaredWithClearedConditionsAndTheNewGeneration() throws InterruptedException {
        useCase.declare(manifest("nomad"), ENV);
        force(Phase.FAILED, 1L);
        queue.take(); // vide la file : le Declare ci-dessous doit y remettre le service
        queue.done(NEWS);

        DeclareResult result = useCase.declare(manifest("nomad"), ENV);

        assertEquals(2L, result.generation());
        assertEquals(Phase.DECLARED, status().phase());
        assertEquals(List.of(), status().conditions(), "l'ancien échec ne doit plus être lisible");
        assertEquals(2L, status().observedGeneration());
        queue.shutdown();
        assertEquals(NEWS, queue.take(), "le service doit être remis dans la file de travail");
    }

    @Test
    void aStoppedServiceIsRelaunchedFromDeclared() {
        useCase.declare(manifest("nomad"), ENV);
        force(Phase.STOPPED, 1L);

        useCase.declare(manifest("nomad"), ENV);

        assertEquals(Phase.DECLARED, status().phase());
        assertEquals(List.of(), status().conditions());
        assertEquals(2L, status().observedGeneration());
    }

    @Test
    void everyOtherPhaseIsLeftExactlyAsItWas() {
        useCase.declare(manifest("nomad"), ENV);
        for (Phase phase : Phase.values()) {
            if (phase == Phase.FAILED || phase == Phase.STOPPED) {
                continue;
            }
            force(phase, 1L);
            ServiceStatus before = status();

            useCase.declare(manifest("nomad"), ENV);

            assertEquals(before, status(), "un Declare ne doit pas toucher le statut d'un service en " + phase);
        }
    }

    @Test
    void aRefusedDeclareLeavesAFailedServiceUntouched() {
        useCase.declare(manifest("nomad"), ENV);
        force(Phase.FAILED, 1L);
        ServiceStatus before = status();

        assertThrows(UnknownDeploymentAdapterException.class, () -> useCase.declare(manifest("docker"), ENV));
        assertEquals(before, status(), "adapter non activé : le service doit rester FAILED");

        assertThrows(DeploymentAdapterChangeException.class, () -> useCase.declare(manifest("kubernetes"), ENV));
        assertEquals(before, status(), "adapter changé : le service doit rester FAILED");
        assertEquals(1L, registry.findById(NEWS).orElseThrow().generation(), "aucune génération ne doit être ajoutée");
    }

    @Test
    void aBrandNewServiceHasNoStatusToResetAndIsCreatedNormally() {
        useCase.declare(manifest("nomad"), ENV);

        assertTrue(registry.findStatus(NEWS).isEmpty(), "le statut initial est créé par le moteur, pas par le Declare");
    }
}
