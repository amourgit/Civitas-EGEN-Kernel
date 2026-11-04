package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.EnabledDeploymentAdapters;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.reconciliation.PendingDeclareTraces;
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
    private final PendingDeclareTraces pendingDeclareTraces;
    private final EnabledDeploymentAdapters enabledAdapters;

    public DeployServiceUseCaseImpl(RegistryStorePort registryStorePort, WorkQueue workQueue) {
        this(registryStorePort, workQueue, new PendingDeclareTraces());
    }

    public DeployServiceUseCaseImpl(RegistryStorePort registryStorePort, WorkQueue workQueue,
                                     PendingDeclareTraces pendingDeclareTraces) {
        this(registryStorePort, workQueue, pendingDeclareTraces, EnabledDeploymentAdapters.acceptingAny());
    }

    /** Constructeur de production : {@code enabledAdapters} liste les adapters activés dans ce Kernel. */
    public DeployServiceUseCaseImpl(RegistryStorePort registryStorePort, WorkQueue workQueue,
                                     PendingDeclareTraces pendingDeclareTraces,
                                     EnabledDeploymentAdapters enabledAdapters) {
        this.registryStorePort = registryStorePort;
        this.workQueue = workQueue;
        this.pendingDeclareTraces = pendingDeclareTraces;
        this.enabledAdapters = enabledAdapters;
    }

    @Override
    public DeclareResult declare(ServiceManifest manifest, TargetEnvironment targetEnvironment) {
        return declare(manifest, targetEnvironment, null);
    }

    @Override
    public DeclareResult declare(ServiceManifest manifest, TargetEnvironment targetEnvironment,
                                  TraceContext triggeringTrace) {
        assertAdapterIsEnabledAndUnchanged(manifest);
        assertNoCycleIntroduced(manifest);

        DesiredState saved = registryStorePort.save(new DesiredState(manifest, 0L, targetEnvironment));
        pendingDeclareTraces.record(saved.serviceId(), saved.generation(), triggeringTrace);
        workQueue.enqueue(saved.serviceId());
        return new DeclareResult(saved.serviceId(), saved.generation());
    }

    /**
     * Reconstruit le graphe de dependances de tout l'ecosysteme CONNU
     * (voir 10.1 : jamais seulement local a ce manifeste) en y injectant ce
     * nouveau manifeste, et rejette le Declare si un cycle apparait —
     * quelle que soit sa longueur.
     */
    /**
     * Étapes 2 et 3 de la validation (docs/architecture/06-service-manifest.md) : l'adapter déclaré est
     * activé dans ce Kernel, et il ne change pas pour un service qui existe déjà (docs/architecture/07,
     * « Sélection et routage »). Avant toute persistance : un refus ne laisse aucune trace.
     */
    private void assertAdapterIsEnabledAndUnchanged(ServiceManifest incoming) {
        String requested = incoming.deployment().adapter();
        if (!enabledAdapters.isEnabled(requested)) {
            throw new UnknownDeploymentAdapterException(requested, enabledAdapters.enabledNames());
        }
        registryStorePort.findById(incoming.id()).ifPresent(existing -> {
            String current = existing.manifest().deployment().adapter();
            if (!current.equals(requested)) {
                throw new DeploymentAdapterChangeException(incoming.id(), current, requested);
            }
        });
    }

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
