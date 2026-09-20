package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentException;
import africa.civitas.egen.application.port.DeploymentHandle;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceVersion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Double de test EN MEMOIRE, prive a egen-application (voir la note dans
 * README de ce module au sujet du cycle egen-test-kit &lt;-&gt; egen-application).
 * Simule un DeploymentPort dont la convergence est pilotable par le test :
 * healthyCount atteint desiredCount seulement apres {@code cyclesToConverge}
 * appels a getStatus(), pour exercer la boucle "DEPLOYING -> RUNNING" sans
 * vrai Nomad.
 */
final class FakeDeploymentPort implements DeploymentPort {

    private final Map<ServiceId, Integer> observeCallCount = new ConcurrentHashMap<>();
    private final Map<ServiceId, Integer> createCallCount = new ConcurrentHashMap<>();
    private int cyclesToConverge = 1;
    private boolean alwaysThrowOnCreate = false;

    void setCyclesToConverge(int cycles) {
        this.cyclesToConverge = cycles;
    }

    void setAlwaysThrowOnCreate(boolean value) {
        this.alwaysThrowOnCreate = value;
    }

    int createCallCount(ServiceId id) {
        return createCallCount.getOrDefault(id, 0);
    }

    @Override
    public DeploymentHandle create(ServiceId id, DeploymentSpec spec) {
        if (alwaysThrowOnCreate) {
            throw new DeploymentException("panne simulee du moteur de deploiement");
        }
        createCallCount.merge(id, 1, Integer::sum);
        return new DeploymentHandle(id, "fake-" + id.value());
    }

    @Override
    public void update(ServiceId id, DeploymentSpec newSpec) {
        // non exerce par les tests de niveau 2 actuels
    }

    @Override
    public void scale(ServiceId id, int replicas) {
        // non exerce par les tests de niveau 2 actuels
    }

    @Override
    public void restart(ServiceId id) {
        // non exerce par les tests de niveau 2 actuels
    }

    @Override
    public void stop(ServiceId id) {
        // non exerce par les tests de niveau 2 actuels
    }

    @Override
    public void remove(ServiceId id) {
        // non exerce par les tests de niveau 2 actuels
    }

    @Override
    public DeploymentObservation getStatus(ServiceId id) {
        int calls = observeCallCount.merge(id, 1, Integer::sum);
        int desired = 2;
        boolean converged = calls >= cyclesToConverge;
        int healthy = converged ? desired : 0;
        return new DeploymentObservation(desired, healthy, healthy, 0, Instant.now());
    }

    @Override
    public List<AllocationInfo> listAllocations(ServiceId id) {
        return List.of();
    }

    @Override
    public void rollback(ServiceId id, ServiceVersion targetVersion) {
        // non exerce par les tests de niveau 2 actuels
    }
}
