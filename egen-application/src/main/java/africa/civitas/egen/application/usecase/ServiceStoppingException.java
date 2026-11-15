package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.ServiceId;

/**
 * Un {@code Declare} arrive pendant l'arret du service ({@code STOPPING}). Refuse : une nouvelle generation
 * enregistree pendant l'arret n'aurait aucun effet defini (docs/architecture/09, « Declare pendant un arret »,
 * ecart I19). L'API repond 409 ; il faut attendre {@code STOPPED}, puis declarer a nouveau.
 */
public final class ServiceStoppingException extends IllegalStateException {
    public ServiceStoppingException(ServiceId id) {
        super("Le service \"" + id + "\" est en cours d'arret (STOPPING) : attendre la phase STOPPED, puis le "
                + "declarer a nouveau.");
    }
}
