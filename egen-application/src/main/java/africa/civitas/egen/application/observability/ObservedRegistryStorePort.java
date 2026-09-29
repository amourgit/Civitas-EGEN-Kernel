package africa.civitas.egen.application.observability;

import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.List;
import java.util.Optional;

/**
 * Decore un {@link RegistryStorePort} concret des metriques generiques
 * d'appel d'adapter — voir {@link ObservedDeploymentPort}. S'applique
 * aussi bien a l'implementation PostgreSQL de production qu'a une
 * implementation en memoire de test si elle est un jour cablee derriere
 * ce decorateur.
 */
public final class ObservedRegistryStorePort implements RegistryStorePort {

    private final RegistryStorePort delegate;
    private final AdapterCallInstrumentation instrumentation;

    public ObservedRegistryStorePort(RegistryStorePort delegate, ObservabilityPort observabilityPort,
                                      String adapterName) {
        this.delegate = delegate;
        this.instrumentation = new AdapterCallInstrumentation(observabilityPort, adapterName);
    }

    @Override
    public DesiredState save(DesiredState desiredState) {
        return instrumentation.call("registry.save", () -> delegate.save(desiredState));
    }

    @Override
    public Optional<DesiredState> findById(ServiceId id) {
        return instrumentation.call("registry.findById", () -> delegate.findById(id));
    }

    @Override
    public void saveStatus(ServiceId id, ServiceStatus status) {
        instrumentation.run("registry.saveStatus", () -> delegate.saveStatus(id, status));
    }

    @Override
    public Optional<ServiceStatus> findStatus(ServiceId id) {
        return instrumentation.call("registry.findStatus", () -> delegate.findStatus(id));
    }

    @Override
    public List<ServiceId> findAllIds() {
        return instrumentation.call("registry.findAllIds", delegate::findAllIds);
    }

    @Override
    public List<DesiredState> history(ServiceId id) {
        return instrumentation.call("registry.history", () -> delegate.history(id));
    }
}
