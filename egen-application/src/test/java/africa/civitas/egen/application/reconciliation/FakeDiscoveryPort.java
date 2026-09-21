package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.ResolvedInstance;
import africa.civitas.egen.application.port.ResolvedInstances;
import africa.civitas.egen.application.port.ServiceInstanceRegistration;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Double de test EN MEMOIRE, prive a egen-application. Modelise un registre
 * Consul minimal : register()/deregister() par {@code instanceId}, resolve()
 * retourne tout ce qui reste enregistre (toutes les instances de ce double
 * sont considerees "saines" des l'enregistrement — pas de simulation de
 * health check independant a ce niveau).
 */
final class FakeDiscoveryPort implements DiscoveryPort {

    private final Map<ServiceId, Map<String, ResolvedInstance>> registered = new ConcurrentHashMap<>();
    private boolean alwaysThrowOnRegister = false;

    void setAlwaysThrowOnRegister(boolean value) {
        this.alwaysThrowOnRegister = value;
    }

    int registeredCount(ServiceId id) {
        return registered.getOrDefault(id, Map.of()).size();
    }

    @Override
    public void register(ServiceInstanceRegistration registration) {
        if (alwaysThrowOnRegister) {
            throw new africa.civitas.egen.application.port.DiscoveryException(
                    "panne simulee du moteur de decouverte");
        }
        registered.computeIfAbsent(registration.serviceId(), ignored -> new ConcurrentHashMap<>())
                .put(registration.instanceId(), new ResolvedInstance(registration.instanceId(),
                        registration.address(), registration.port()));
    }

    @Override
    public void deregister(ServiceId id, String instanceId) {
        Map<String, ResolvedInstance> instances = registered.get(id);
        if (instances != null) {
            instances.remove(instanceId);
        }
    }

    @Override
    public ResolvedInstances resolve(ServiceId id) {
        return new ResolvedInstances(List.copyOf(registered.getOrDefault(id, Map.of()).values()));
    }
}
