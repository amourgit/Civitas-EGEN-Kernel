package africa.civitas.egen.application.resilience;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.Objects;

/** Lit la politique dans le manifeste desire de la cible (la source de verite, voir docs/architecture/08). */
public final class RegistryResiliencyPolicyProvider implements ResiliencyPolicyProvider {

    private final RegistryStorePort registryStorePort;

    public RegistryResiliencyPolicyProvider(RegistryStorePort registryStorePort) {
        this.registryStorePort = Objects.requireNonNull(registryStorePort, "registryStorePort");
    }

    @Override
    public ResiliencyPolicy policyFor(ServiceId target) {
        return registryStorePort.findById(target)
                .map(desiredState -> desiredState.manifest().resiliency())
                .orElseGet(ResiliencyPolicy::defaults);
    }
}
