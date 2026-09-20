package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.ServiceId;

/**
 * Port primaire — lecture du statut d'un service ("Observe" cote client,
 * voir docs/architecture/13-api-et-contrats.md, "GET /services/{id}/status").
 */
public interface GetServiceStatusUseCase {

    /**
     * @throws ServiceNotFoundException si aucun DesiredState n'a jamais ete
     *         declare pour cet identifiant
     */
    ServiceStatus getStatus(ServiceId id);
}
