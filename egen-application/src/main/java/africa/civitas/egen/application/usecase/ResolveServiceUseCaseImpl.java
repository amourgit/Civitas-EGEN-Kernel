package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.resilience.ResiliencyPolicyProvider;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.Objects;

public final class ResolveServiceUseCaseImpl implements ResolveServiceUseCase {

    private final DiscoveryPort discoveryPort;
    private final ResiliencyPolicyProvider policyProvider;

    public ResolveServiceUseCaseImpl(DiscoveryPort discoveryPort, ResiliencyPolicyProvider policyProvider) {
        this.discoveryPort = Objects.requireNonNull(discoveryPort, "discoveryPort");
        this.policyProvider = Objects.requireNonNull(policyProvider, "policyProvider");
    }

    @Override
    public ServiceResolution resolve(ServiceId id) {
        return new ServiceResolution(id, discoveryPort.resolve(id), policyProvider.policyFor(id));
    }
}
