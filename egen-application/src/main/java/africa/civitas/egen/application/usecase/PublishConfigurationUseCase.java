package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;

import java.util.Map;

/**
 * Port primaire : publie la configuration technique d'un service (A3, docs/architecture/13-api-et-contrats.md,
 * « Publication de configuration »). Remplacement complet du jeu de valeurs pour le couple (service, environnement).
 */
public interface PublishConfigurationUseCase {

    /**
     * @throws ServiceNotFoundException              le service n'est pas declare
     * @throws InvalidConfigurationException         une ou plusieurs regles de validation sont enfreintes
     * @throws ConfigurationStoreUnavailableException le store est injoignable : rien n'est publie
     */
    PublishedConfiguration publish(ServiceId id, TargetEnvironment environment, Map<String, String> values);
}
