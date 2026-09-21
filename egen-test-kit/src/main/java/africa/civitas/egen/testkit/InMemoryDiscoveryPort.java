package africa.civitas.egen.testkit;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.ResolvedInstance;
import africa.civitas.egen.application.port.ResolvedInstances;
import africa.civitas.egen.application.port.ServiceInstanceRegistration;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Double de test EN MEMOIRE d'un {@link DiscoveryPort}, pour les modules
 * AVAL d'egen-application (voir la description d'egen-test-kit). Toute
 * instance enregistree est consideree saine immediatement — pas de
 * simulation de health check independant a ce niveau.
 */
public final class InMemoryDiscoveryPort implements DiscoveryPort {

    private final Map<ServiceId, Map<String, ResolvedInstance>> registered = new ConcurrentHashMap<>();

    @Override
    public void register(ServiceInstanceRegistration registration) {
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
