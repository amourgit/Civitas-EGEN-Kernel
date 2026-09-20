package africa.civitas.egen.testkit;

import africa.civitas.egen.application.port.AllocationInfo;
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

/**
 * Double de test EN MEMOIRE d'un {@link DeploymentPort}, pour les modules
 * AVAL d'egen-application (voir la description de ce module). Simule une
 * convergence immediate : toute spec creee est aussitot "healthy" a hauteur
 * de son nombre minimal de replicas, sans jamais parler a un vrai moteur.
 */
public final class InMemoryDeploymentPort implements DeploymentPort {

    private final Map<ServiceId, DeploymentSpec> deployed = new ConcurrentHashMap<>();
    private final Map<ServiceId, Boolean> stopped = new ConcurrentHashMap<>();

    @Override
    public DeploymentHandle create(ServiceId id, DeploymentSpec spec) {
        deployed.put(id, spec);
        stopped.remove(id);
        return new DeploymentHandle(id, "in-memory-" + id.value());
    }

    @Override
    public void update(ServiceId id, DeploymentSpec newSpec) {
        deployed.put(id, newSpec);
    }

    @Override
    public void scale(ServiceId id, int replicas) {
        DeploymentSpec current = deployed.get(id);
        if (current != null) {
            deployed.put(id, new DeploymentSpec(current.adapter(), current.image(), current.cpu(),
                    current.memory(), new africa.civitas.egen.domain.model.ReplicaRange(replicas, replicas)));
        }
    }

    @Override
    public void restart(ServiceId id) {
        // no-op : rien a redemarrer en memoire
    }

    @Override
    public void stop(ServiceId id) {
        stopped.put(id, true);
    }

    @Override
    public void remove(ServiceId id) {
        deployed.remove(id);
        stopped.remove(id);
    }

    @Override
    public DeploymentObservation getStatus(ServiceId id) {
        DeploymentSpec spec = deployed.get(id);
        if (spec == null || Boolean.TRUE.equals(stopped.get(id))) {
            return DeploymentObservation.empty();
        }
        int desired = spec.replicas().min();
        return new DeploymentObservation(desired, desired, desired, 0, Instant.now());
    }

    @Override
    public List<AllocationInfo> listAllocations(ServiceId id) {
        return List.of();
    }

    @Override
    public void rollback(ServiceId id, ServiceVersion targetVersion) {
        // no-op : pas d'historique de version simule en V1 du double
    }
}
