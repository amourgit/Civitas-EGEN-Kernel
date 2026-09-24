package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.domain.dependency.DependencyGraph;
import africa.civitas.egen.domain.model.Dependency;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.TargetEnvironment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Declare" du cycle canonique (voir
 * docs/architecture/02-principes-fondamentaux.md) : valide (deja fait par le
 * constructeur de {@link ServiceManifest}, appele par l'appelant avant ce
 * use case, PLUS la detection de cycle au niveau de l'ecosysteme entier —
 * voir docs/architecture/10-gestion-des-dependances.md), persiste, puis
 * enqueue — ne delegue JAMAIS directement a un DeploymentPort ici (voir
 * docs/architecture/04-moteur-de-reconciliation.md, "Anti-pattern a
 * bannir : la reconciliation synchrone dans l'API").
 */
public final class DeployServiceUseCaseImpl implements DeployServiceUseCase {

    private final RegistryStorePort registryStorePort;
    private final WorkQueue workQueue;

    public DeployServiceUseCaseImpl(RegistryStorePort registryStorePort, WorkQueue workQueue) {
        this.registryStorePort = registryStorePort;
        this.workQueue = workQueue;
    }

    @Override
    public DeclareResult declare(ServiceManifest manifest, TargetEnvironment targetEnvironment) {
        assertNoCycleIntroduced(manifest);

        DesiredState saved = registryStorePort.save(new DesiredState(manifest, 0L, targetEnvironment));
        workQueue.enqueue(saved.serviceId());
        return new DeclareResult(saved.serviceId(), saved.generation());
    }

    /**
     * Reconstruit le graphe de dependances de tout l'ecosysteme CONNU
     * (voir 10.1 : jamais seulement local a ce manifeste) en y injectant ce
     * nouveau manifeste, et rejette le Declare si un cycle apparait —
     * quelle que soit sa longueur.
     */
    private void assertNoCycleIntroduced(ServiceManifest incoming) {
        Map<ServiceId, Set<ServiceId>> edges = new HashMap<>();
        for (ServiceId id : registryStorePort.findAllIds()) {
            registryStorePort.findById(id).ifPresent(existing -> edges.put(id,
                    toDependencyIds(existing.manifest().dependencies())));
        }
        edges.put(incoming.id(), toDependencyIds(incoming.dependencies()));

        DependencyGraph.of(edges); // leve CyclicDependencyException si invalide
    }

    private static Set<ServiceId> toDependencyIds(java.util.List<Dependency> dependencies) {
        return dependencies.stream().map(Dependency::serviceId).collect(Collectors.toCollection(HashSet::new));
    }
}
