package africa.civitas.egen.application.observability;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.ResolvedInstances;
import africa.civitas.egen.application.port.ServiceInstanceRegistration;
import africa.civitas.egen.domain.model.ServiceId;

/**
 * Decore un {@link DiscoveryPort} concret des metriques generiques d'appel
 * d'adapter — voir {@link ObservedDeploymentPort} pour le detail du
 * raisonnement, identique ici.
 */
public final class ObservedDiscoveryPort implements DiscoveryPort {

    private final DiscoveryPort delegate;
    private final AdapterCallInstrumentation instrumentation;

    public ObservedDiscoveryPort(DiscoveryPort delegate, ObservabilityPort observabilityPort, String adapterName) {
        this.delegate = delegate;
        this.instrumentation = new AdapterCallInstrumentation(observabilityPort, adapterName);
    }

    @Override
    public void register(ServiceInstanceRegistration registration) {
        instrumentation.run("discovery.register", () -> delegate.register(registration));
    }

    @Override
    public void deregister(ServiceId id, String instanceId) {
        instrumentation.run("discovery.deregister", () -> delegate.deregister(id, instanceId));
    }

    @Override
    public ResolvedInstances resolve(ServiceId id) {
        return instrumentation.call("discovery.resolve", () -> delegate.resolve(id));
    }
}
