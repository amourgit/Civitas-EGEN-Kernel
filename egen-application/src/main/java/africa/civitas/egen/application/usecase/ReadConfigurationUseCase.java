package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;

/** Port primaire : relit la configuration publiee d'un service (A3, docs 13). */
public interface ReadConfigurationUseCase {

    /**
     * @throws ServiceNotFoundException              seulement si le service n'est pas declare ; une configuration
     *                                               jamais publiee est vide, de version 0
     * @throws ConfigurationStoreUnavailableException le store est injoignable
     */
    ServiceConfiguration read(ServiceId id, TargetEnvironment environment);
}
