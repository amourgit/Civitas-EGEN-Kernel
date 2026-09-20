package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.ServiceId;

/** Aucun DesiredState n'a ete declare pour ce ServiceId. */
public final class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(ServiceId id) {
        super("Aucun service declare avec l'identifiant : " + id);
    }
}
