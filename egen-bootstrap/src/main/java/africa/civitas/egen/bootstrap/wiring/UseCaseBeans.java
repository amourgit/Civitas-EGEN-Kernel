package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.port.WorkflowStorePort;
import africa.civitas.egen.application.reconciliation.PendingDeclareTraces;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.application.resilience.ResiliencyPolicyProvider;
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
import africa.civitas.egen.application.workflow.WorkflowEnginePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Cablage des ports primaires (use cases) vers leurs implementations — voir
 * docs/architecture/16-packages-et-stack-technique.md. egen-application ne
 * connait jamais CDI ; c'est ici, et seulement ici, que ses implementations
 * concretes sont instanciees et exposees comme beans.
 */
@ApplicationScoped
public class UseCaseBeans {

    @Inject
    RegistryStorePort registryStorePort;

    @Inject
    WorkQueue workQueue;

    @Inject
    PendingDeclareTraces pendingDeclareTraces;

    @Inject
    WorkflowStorePort workflowStorePort;

    @Inject
    WorkflowEnginePort workflowEnginePort;

    @Inject
    DiscoveryPort discoveryPort;

    @Inject
    ResiliencyPolicyProvider resiliencyPolicyProvider;

    @Produces
    @ApplicationScoped
    public DeployServiceUseCase deployServiceUseCase() {
        return new DeployServiceUseCaseImpl(registryStorePort, workQueue, pendingDeclareTraces);
    }

    @Produces
    @ApplicationScoped
    public GetServiceStatusUseCase getServiceStatusUseCase() {
        return new GetServiceStatusUseCaseImpl(registryStorePort);
    }

    @Produces
    @ApplicationScoped
    public StopServiceUseCase stopServiceUseCase() {
        return new StopServiceUseCaseImpl(registryStorePort, workQueue, pendingDeclareTraces);
    }

    @Produces
    @ApplicationScoped
    public DefineWorkflowUseCase defineWorkflowUseCase() {
        return new DefineWorkflowUseCaseImpl(workflowStorePort);
    }

    @Produces
    @ApplicationScoped
    public RunWorkflowUseCase runWorkflowUseCase() {
        return new RunWorkflowUseCaseImpl(workflowStorePort, workflowEnginePort);
    }

    @Produces
    @ApplicationScoped
    public ResolveServiceUseCase resolveServiceUseCase() {
        return new ResolveServiceUseCaseImpl(discoveryPort, resiliencyPolicyProvider);
    }

    @Produces
    @ApplicationScoped
    public ReadServiceManifestUseCase readServiceManifestUseCase() {
        return new ReadServiceManifestUseCaseImpl(registryStorePort);
    }
}

