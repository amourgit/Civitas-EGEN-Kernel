package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.ServiceId;

public final class GetServiceStatusUseCaseImpl implements GetServiceStatusUseCase {

    private final RegistryStorePort registryStorePort;

    public GetServiceStatusUseCaseImpl(RegistryStorePort registryStorePort) {
        this.registryStorePort = registryStorePort;
    }

    @Override
    public ServiceStatus getStatus(ServiceId id) {
        if (registryStorePort.findById(id).isEmpty()) {
            throw new ServiceNotFoundException(id);
        }
        return registryStorePort.findStatus(id).orElse(ServiceStatus.initial());
    }
}
