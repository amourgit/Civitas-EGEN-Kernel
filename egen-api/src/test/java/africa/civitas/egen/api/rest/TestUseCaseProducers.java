package africa.civitas.egen.api.rest;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.application.usecase.DeployServiceUseCase;
import africa.civitas.egen.application.usecase.DeployServiceUseCaseImpl;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCase;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCaseImpl;
import africa.civitas.egen.application.usecase.StopServiceUseCase;
import africa.civitas.egen.application.usecase.StopServiceUseCaseImpl;
import africa.civitas.egen.testkit.InMemoryDiscoveryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Cablage CDI minimal pour les tests de ce module uniquement — la
 * composition reelle (domaine + adapters) vit dans egen-bootstrap, seul
 * module autorise a connaitre les deux simultanement (voir
 * docs/architecture/16-packages-et-stack-technique.md). Ici, de simples
 * doubles en memoire (egen-test-kit) suffisent a exercer les controleurs
 * REST sans dependre d'un vrai Nomad ni d'un vrai Consul.
 */
@ApplicationScoped
public class TestUseCaseProducers {

    private final RegistryStorePort registryStorePort = new InMemoryRegistryStore();
    private final WorkQueue workQueue = new WorkQueue();
    private final DiscoveryPort discoveryPort = new InMemoryDiscoveryPort();

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

    @Produces
    @ApplicationScoped
    StopServiceUseCase stopServiceUseCase() {
        return new StopServiceUseCaseImpl(registryStorePort, workQueue);
    }

    @Produces
    @ApplicationScoped
    DiscoveryPort discoveryPort() {
        return discoveryPort;
    }
}
