package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.application.usecase.DeployServiceUseCase;
import africa.civitas.egen.application.usecase.DeployServiceUseCaseImpl;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCase;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCaseImpl;
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

    @Produces
    @ApplicationScoped
    public DeployServiceUseCase deployServiceUseCase() {
        return new DeployServiceUseCaseImpl(registryStorePort, workQueue);
    }

    @Produces
    @ApplicationScoped
    public GetServiceStatusUseCase getServiceStatusUseCase() {
        return new GetServiceStatusUseCaseImpl(registryStorePort);
    }
}
