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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Double de test EN MEMOIRE, prive a egen-application (voir la note dans
 * le README de ce module au sujet du cycle egen-test-kit &lt;-&gt;
 * egen-application). Simule une convergence de deploiement pilotable par le
 * test : healthyCount atteint desiredCount seulement apres
 * {@code cyclesToConverge} appels a getStatus(), et {@code listAllocations}
 * retourne des allocations "running" avec des infos reseau des que
 * convergees, pour exercer l'enregistrement Discovery en aval.
 */
final class FakeDeploymentPort implements DeploymentPort {

    private final Map<ServiceId, Integer> observeCallCount = new ConcurrentHashMap<>();
    private final Map<ServiceId, Integer> createCallCount = new ConcurrentHashMap<>();
    private int cyclesToConverge = 1;
    private int desiredCount = 2;
    private boolean alwaysThrowOnCreate = false;

    void setCyclesToConverge(int cycles) {
        this.cyclesToConverge = cycles;
    }

    void setDesiredCount(int desiredCount) {
        this.desiredCount = desiredCount;
    }

    void setAlwaysThrowOnCreate(boolean value) {
        this.alwaysThrowOnCreate = value;
    }

    int createCallCount(ServiceId id) {
        return createCallCount.getOrDefault(id, 0);
    }

    private boolean isConvergedNow(ServiceId id) {
        return observeCallCount.getOrDefault(id, 0) >= cyclesToConverge;
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
    }

    @Override
    public void scale(ServiceId id, int replicas) {
    }

    @Override
    public void restart(ServiceId id) {
    }

    @Override
    public void stop(ServiceId id) {
    }

    @Override
    public void remove(ServiceId id) {
    }

    @Override
    public DeploymentObservation getStatus(ServiceId id) {
        int calls = observeCallCount.merge(id, 1, Integer::sum);
        boolean converged = calls >= cyclesToConverge;
        int healthy = converged ? desiredCount : 0;
        return new DeploymentObservation(desiredCount, healthy, healthy, 0, Instant.now());
    }

    @Override
    public List<AllocationInfo> listAllocations(ServiceId id) {
        if (!isConvergedNow(id)) {
            return List.of();
        }
        List<AllocationInfo> allocations = new ArrayList<>();
        for (int i = 0; i < desiredCount; i++) {
            allocations.add(new AllocationInfo(id.value() + "-alloc-" + i, "running", Instant.now(),
                    "10.0.0." + (i + 1), 8080));
        }
        return allocations;
    }

    @Override
    public void rollback(ServiceId id, ServiceVersion targetVersion) {
    }
}
