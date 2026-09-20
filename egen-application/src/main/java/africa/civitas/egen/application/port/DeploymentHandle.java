package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.model.ServiceId;

/**
 * Reference neutre vers un deploiement cree par un {@link DeploymentPort} —
 * ne contient aucun type du moteur specialise sous-jacent (voir
 * docs/architecture/07-ports-et-adapters.md).
 */
public record DeploymentHandle(ServiceId serviceId, String adapterNativeId) {

    public DeploymentHandle {
        if (serviceId == null) {
            throw new IllegalArgumentException("DeploymentHandle.serviceId est obligatoire");
        }
        if (adapterNativeId == null || adapterNativeId.isBlank()) {
            throw new IllegalArgumentException("DeploymentHandle.adapterNativeId ne peut pas etre vide");
        }
    }
}
