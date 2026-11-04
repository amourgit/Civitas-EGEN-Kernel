package africa.civitas.egen.application.routing;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentException;
import africa.civitas.egen.application.port.DeploymentHandle;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceVersion;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Le seul {@link DeploymentPort} que voit le moteur de réconciliation : il choisit, pour chaque service,
 * l'adapter désigné par {@code deployment.adapter} de son manifeste, puis lui délègue l'appel (voir
 * docs/architecture/07-ports-et-adapters.md, « Sélection et routage »).
 *
 * <p>Il ne contient aucune logique de déploiement (garde-fou n3,
 * docs/architecture/02-principes-fondamentaux.md) et ne connaît aucun adapter concret : il reçoit des
 * {@link DeploymentPort} par nom. Pour {@code create} et {@code update}, le nom vient de la
 * {@link DeploymentSpec} reçue ; pour les autres opérations, du manifeste courant du service dans le
 * Registry. Pas de repli silencieux : un adapter introuvable est une {@link DeploymentException}.</p>
 */
public final class RoutingDeploymentPort implements DeploymentPort {

    private final Map<String, DeploymentPort> adapters;
    private final RegistryStorePort registryStorePort;

    public RoutingDeploymentPort(Map<String, DeploymentPort> adapters, RegistryStorePort registryStorePort) {
        if (adapters == null || adapters.isEmpty()) {
            throw new IllegalArgumentException("Le routeur a besoin d'au moins un Deployment Adapter");
        }
        this.adapters = Map.copyOf(new TreeMap<>(adapters));
        this.registryStorePort = registryStorePort;
    }

    @Override
    public DeploymentHandle create(ServiceId id, DeploymentSpec spec) {
        return adapterNamed(spec.adapter(), id).create(id, spec);
    }

    @Override
    public void update(ServiceId id, DeploymentSpec newSpec) {
        adapterNamed(newSpec.adapter(), id).update(id, newSpec);
    }

    @Override
    public void scale(ServiceId id, int replicas) {
        adapterOf(id).scale(id, replicas);
    }

    @Override
    public void restart(ServiceId id) {
        adapterOf(id).restart(id);
    }

    @Override
    public void stop(ServiceId id) {
        adapterOf(id).stop(id);
    }

    @Override
    public void remove(ServiceId id) {
        adapterOf(id).remove(id);
    }

    @Override
    public DeploymentObservation getStatus(ServiceId id) {
        // Un service absent du Registry n'a rien à observer : lecture vide, pas une erreur.
        return declaredAdapterName(id).map(name -> adapterNamed(name, id).getStatus(id))
                .orElseGet(DeploymentObservation::empty);
    }

    @Override
    public List<AllocationInfo> listAllocations(ServiceId id) {
        return declaredAdapterName(id).map(name -> adapterNamed(name, id).listAllocations(id))
                .orElseGet(List::of);
    }

    @Override
    public void rollback(ServiceId id, ServiceVersion targetVersion) {
        adapterOf(id).rollback(id, targetVersion);
    }

    // ------------------------------------------------------------------

    /** Adapter d'un service pour une opération qui modifie quelque chose : service inconnu = erreur. */
    private DeploymentPort adapterOf(ServiceId id) {
        String name = declaredAdapterName(id).orElseThrow(() -> new DeploymentException(
                "Service \"" + id + "\" inconnu du Registry : impossible de choisir son Deployment Adapter"));
        return adapterNamed(name, id);
    }

    private Optional<String> declaredAdapterName(ServiceId id) {
        return registryStorePort.findById(id).map(DesiredState::manifest)
                .map(manifest -> manifest.deployment().adapter());
    }

    private DeploymentPort adapterNamed(String name, ServiceId id) {
        DeploymentPort adapter = adapters.get(name);
        if (adapter == null) {
            throw new DeploymentException("Le service \"" + id + "\" demande l'adapter \"" + name
                    + "\", qui n'est pas activé dans ce Kernel ; adapters activés : "
                    + String.join(", ", adapters.keySet()));
        }
        return adapter;
    }
}
