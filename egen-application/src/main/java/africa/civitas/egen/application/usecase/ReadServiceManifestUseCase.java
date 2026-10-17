package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.List;

/**
 * Port primaire — lecture des manifestes declares (voir docs/architecture/13-api-et-contrats.md,
 * {@code /api/v1/services}). Lecture seule : ne declenche ni reconciliation ni deploiement, et ne
 * renvoie que ce que le Registry a persiste — le Kernel n'interprete rien de ce qu'il restitue.
 */
public interface ReadServiceManifestUseCase {

    /** @throws ServiceNotFoundException si le service n'a jamais ete declare */
    DesiredState get(ServiceId id);

    /** Tous les services declares, par identifiant croissant. */
    List<DesiredState> list();
}
