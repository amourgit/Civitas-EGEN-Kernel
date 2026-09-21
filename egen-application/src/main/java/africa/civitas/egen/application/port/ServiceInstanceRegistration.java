package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.ServiceId;

/**
 * Ce qu'il faut pour enregistrer UNE instance aupres du
 * {@link DiscoveryPort} — voir docs/architecture/07-ports-et-adapters.md,
 * "Discovery Port". Le health check est inclus directement dans
 * l'enregistrement (mapping Consul : un seul appel
 * PUT /v1/agent/service/register porte le bloc Check — voir 07.2.2),
 * plutot qu'un second appel separe.
 */
public record ServiceInstanceRegistration(ServiceId serviceId, String instanceId, String address,
                                           int port, HealthSpec health) {

    public ServiceInstanceRegistration {
        if (serviceId == null) {
            throw new IllegalArgumentException("ServiceInstanceRegistration.serviceId est obligatoire");
        }
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("ServiceInstanceRegistration.instanceId ne peut pas etre vide");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("ServiceInstanceRegistration.address ne peut pas etre vide");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("ServiceInstanceRegistration.port doit etre positif");
        }
        if (health == null) {
            throw new IllegalArgumentException("ServiceInstanceRegistration.health est obligatoire");
        }
    }
}
