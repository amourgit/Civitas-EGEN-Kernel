package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.ConfigurationException;
import africa.civitas.egen.application.port.ConfigurationPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.port.ResolvedConfig;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;

import java.util.Objects;

public final class ReadConfigurationUseCaseImpl implements ReadConfigurationUseCase {

    private final RegistryStorePort registryStorePort;
    private final ConfigurationPort configurationPort;

    public ReadConfigurationUseCaseImpl(RegistryStorePort registryStorePort, ConfigurationPort configurationPort) {
        this.registryStorePort = Objects.requireNonNull(registryStorePort, "registryStorePort");
        this.configurationPort = Objects.requireNonNull(configurationPort, "configurationPort");
    }

    @Override
    public ServiceConfiguration read(ServiceId id, TargetEnvironment environment) {
        if (registryStorePort.findById(id).isEmpty()) {
            throw new ServiceNotFoundException(id);
        }
        ResolvedConfig resolved;
        try {
            resolved = configurationPort.resolve(id, environment);
        } catch (ConfigurationException e) {
            throw new ConfigurationStoreUnavailableException(
                    "Store de configuration indisponible pour " + id.value(), e);
        }
        return new ServiceConfiguration(id, environment, resolved.version().index(), resolved.values());
    }
}
