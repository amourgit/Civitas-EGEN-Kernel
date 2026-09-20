package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.TargetEnvironment;

/**
 * "Declare" du cycle canonique (voir
 * docs/architecture/02-principes-fondamentaux.md) : valide (deja fait par le
 * constructeur de {@link ServiceManifest}, appele par l'appelant avant ce
 * use case), persiste, puis enqueue — ne delegue JAMAIS directement a un
 * DeploymentPort ici (voir docs/architecture/04-moteur-de-reconciliation.md,
 * "Anti-pattern a bannir : la reconciliation synchrone dans l'API").
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
        DesiredState saved = registryStorePort.save(new DesiredState(manifest, 0L, targetEnvironment));
        workQueue.enqueue(saved.serviceId());
        return new DeclareResult(saved.serviceId(), saved.generation());
    }
}
