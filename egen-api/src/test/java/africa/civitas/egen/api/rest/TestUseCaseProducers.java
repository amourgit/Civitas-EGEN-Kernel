package africa.civitas.egen.api.rest;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.port.WorkflowStorePort;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.application.resilience.RegistryResiliencyPolicyProvider;
import africa.civitas.egen.application.usecase.DefineWorkflowUseCase;
import africa.civitas.egen.application.usecase.DefineWorkflowUseCaseImpl;
import africa.civitas.egen.application.usecase.DeployServiceUseCase;
import africa.civitas.egen.application.usecase.DeployServiceUseCaseImpl;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCase;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCaseImpl;
import africa.civitas.egen.application.usecase.ReadServiceManifestUseCase;
import africa.civitas.egen.application.usecase.ReadServiceManifestUseCaseImpl;
import africa.civitas.egen.application.usecase.ResolveServiceUseCase;
import africa.civitas.egen.application.usecase.ResolveServiceUseCaseImpl;
import africa.civitas.egen.application.usecase.RunWorkflowUseCase;
import africa.civitas.egen.application.usecase.RunWorkflowUseCaseImpl;
import africa.civitas.egen.application.usecase.StopServiceUseCase;
import africa.civitas.egen.application.usecase.StopServiceUseCaseImpl;
import africa.civitas.egen.application.workflow.Sleeper;
import africa.civitas.egen.application.workflow.WorkflowEngine;
import africa.civitas.egen.application.workflow.WorkflowEnginePort;
import africa.civitas.egen.application.workflow.WorkflowEngineSettings;
import africa.civitas.egen.testkit.InMemoryDiscoveryPort;
import africa.civitas.egen.testkit.InMemoryWorkflowStore;
import africa.civitas.egen.testkit.NoOpMessagingPort;
import africa.civitas.egen.testkit.NoOpObservabilityPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

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
    private final ObservabilityPort observabilityPort = new NoOpObservabilityPort();
    private final WorkflowStorePort workflowStorePort = new InMemoryWorkflowStore();

    /**
     * Executor qui ne pilote jamais : {@code start()} persiste l'execution
     * RUNNING et s'arrete la. Les tests REST verifient le contrat HTTP
     * (202, Location, codes d'erreur, transitions pause/resume/terminate),
     * pas l'orchestration d'etapes — celle-ci est couverte par
     * WorkflowEngineTest dans egen-application.
     */
    private final WorkflowEnginePort workflowEngine = new WorkflowEngine(workflowStorePort, discoveryPort,
            (endpoint, method, payload, timeout) -> {
                throw new UnsupportedOperationException("aucun appel reseau dans les tests REST");
            },
            new NoOpMessagingPort(), Clock.systemUTC(), Sleeper.threadSleep(), command -> { },
            WorkflowEngineSettings.defaults());

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
    ReadServiceManifestUseCase readServiceManifestUseCase() {
        return new ReadServiceManifestUseCaseImpl(registryStorePort);
    }

    @Produces
    @ApplicationScoped
    ResolveServiceUseCase resolveServiceUseCase() {
        return new ResolveServiceUseCaseImpl(discoveryPort,
                new RegistryResiliencyPolicyProvider(registryStorePort));
    }

    @Produces
    @ApplicationScoped
    DefineWorkflowUseCase defineWorkflowUseCase() {
        return new DefineWorkflowUseCaseImpl(workflowStorePort);
    }

    @Produces
    @ApplicationScoped
    RunWorkflowUseCase runWorkflowUseCase() {
        return new RunWorkflowUseCaseImpl(workflowStorePort, workflowEngine);
    }

    @Produces
    @ApplicationScoped
    RegistryStorePort registryStorePort() {
        return registryStorePort;
    }

    @Produces
    @ApplicationScoped
    DiscoveryPort discoveryPort() {
        return discoveryPort;
    }

    @Produces
    @ApplicationScoped
    ObservabilityPort observabilityPort() {
        return observabilityPort;
    }
}
