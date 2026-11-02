package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentHandle;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Niveau 2 (docs 17). Arreter le Kernel interrompt les workers au milieu d'un appel d'adapter : ce n'est pas
 * un echec de l'adapter (pas de compteur d'echecs consecutifs, qui mene a FAILED, ni de re-planification) —
 * alors qu'une vraie panne, elle, est comptee et re-planifiee avec backoff (04).
 */
class ReconciliationEngineShutdownTest {

    private static final ServiceId ID = ServiceId.of("news-service");

    /** {@code create} echoue ; en mode « arret », le thread est d'abord interrompu, comme le fait un client HTTP. */
    private static final class FailingDeploymentPort implements DeploymentPort {
        private final FakeDeploymentPort delegate = new FakeDeploymentPort();
        boolean interruptBeforeFailing;

        @Override
        public DeploymentHandle create(ServiceId id, DeploymentSpec spec) {
            if (interruptBeforeFailing) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("appel interrompu");
            }
            throw new IllegalStateException("panne reelle du moteur de deploiement");
        }

        @Override public void update(ServiceId id, DeploymentSpec newSpec) { delegate.update(id, newSpec); }
        @Override public void scale(ServiceId id, int replicas) { delegate.scale(id, replicas); }
        @Override public void restart(ServiceId id) { delegate.restart(id); }
        @Override public void stop(ServiceId id) { delegate.stop(id); }
        @Override public void remove(ServiceId id) { delegate.remove(id); }
        @Override public DeploymentObservation getStatus(ServiceId id) { return delegate.getStatus(id); }
        @Override public List<AllocationInfo> listAllocations(ServiceId id) { return delegate.listAllocations(id); }
        @Override public void rollback(ServiceId id, ServiceVersion targetVersion) { delegate.rollback(id, targetVersion); }
    }

    private final InMemoryRegistryStore registry = new InMemoryRegistryStore();
    private final WorkQueue queue = new WorkQueue();
    private final FailingDeploymentPort deployment = new FailingDeploymentPort();
    private final ReconciliationEngine engine = new ReconciliationEngine(queue, registry, deployment,
            new FakeDiscoveryPort(), new NoOpObservabilityPort());

    @AfterEach
    void clearInterruptFlagAndStop() {
        Thread.interrupted(); // le test a pu laisser le drapeau leve sur le thread de test
        queue.shutdown();
    }

    private void declareConfigured() {
        registry.save(new DesiredState(manifest(), 0L, TargetEnvironment.of("test")));
        registry.saveStatus(ID, new ServiceStatus(Phase.CONFIGURED, List.of(), 1L));
    }

    @Test
    void aRealAdapterFailureIsRequeuedWithBackoff() {
        declareConfigured();

        engine.reconcile(ID);

        assertEquals(1, queue.pendingDelays(), "une vraie panne est re-planifiee");
    }

    @Test
    void aCallInterruptedByShutdownIsNotCountedNorRequeued() {
        declareConfigured();
        deployment.interruptBeforeFailing = true;

        engine.reconcile(ID);

        assertEquals(0, queue.pendingDelays(), "arret du Kernel : rien n'est re-planifie");
        assertEquals(Phase.CONFIGURED, registry.findStatus(ID).orElseThrow().phase(), "l'etat n'est pas modifie");
    }

    private static ServiceManifest manifest() {
        return new ServiceManifest(ID, ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry.civitas.africa/news:1.0.0", "java"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news:1.0.0", "500m", "512Mi",
                        new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(Duration.ofMillis(50)));
    }
}
