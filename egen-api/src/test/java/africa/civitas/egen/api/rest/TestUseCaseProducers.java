package africa.civitas.egen.api.rest;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.application.usecase.DeployServiceUseCase;
import africa.civitas.egen.application.usecase.DeployServiceUseCaseImpl;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCase;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCaseImpl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Cablage CDI minimal pour les tests de ce module uniquement — la
 * composition reelle (domaine + adapters) vit dans egen-bootstrap, seul
 * module autorise a connaitre les deux simultanement (voir
 * docs/architecture/16-packages-et-stack-technique.md). Ici, un simple
 * RegistryStorePort en memoire suffit a exercer les controleurs REST sans
 * dependre d'un vrai Nomad.
 */
@ApplicationScoped
public class TestUseCaseProducers {

    private final RegistryStorePort registryStorePort = new InMemoryRegistryStore();
    private final WorkQueue workQueue = new WorkQueue();

    @Produces
    @ApplicationScoped
    DeployServiceUseCase deployServiceUseCase() {
        return new DeployServiceUseCaseImpl(registryStorePort, workQueue);
    }

    @Produces
    @ApplicationScoped
    GetServiceStatusUseCase getServiceStatusUseCase() {
        return new GetServiceStatusUseCaseImpl(registryStorePort);
    }
}
