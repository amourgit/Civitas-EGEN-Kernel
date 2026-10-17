package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ReadServiceManifestUseCaseImpl implements ReadServiceManifestUseCase {

    private final RegistryStorePort registryStorePort;

    public ReadServiceManifestUseCaseImpl(RegistryStorePort registryStorePort) {
        this.registryStorePort = Objects.requireNonNull(registryStorePort, "registryStorePort");
    }

    @Override
    public DesiredState get(ServiceId id) {
        return registryStorePort.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));
    }

    @Override
    public List<DesiredState> list() {
        return registryStorePort.findAllIds().stream()
                .sorted(Comparator.comparing(ServiceId::value))
                .map(registryStorePort::findById)
                .flatMap(java.util.Optional::stream)
                .toList();
    }
}
