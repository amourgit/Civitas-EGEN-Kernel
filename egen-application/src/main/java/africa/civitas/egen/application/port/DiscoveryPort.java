package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.model.ServiceId;

/**
 * Port secondaire — ce que le domaine exige de tout moteur de decouverte
 * (Consul en V1, voir docs/architecture/07-ports-et-adapters.md, "Discovery
 * Port"). {@code health(id, instanceId)} n'est pas repris comme methode
 * separee : la documentation d'adaptation Consul note qu'elle se derive
 * directement de la reponse de {@link #resolve}, sans appel supplementaire
 * — inutile de dupliquer ce que resolve() donne deja.
 *
 * <p><b>Idempotence</b> (garde-fou n7,
 * docs/architecture/02-principes-fondamentaux.md) : {@link #register}
 * rejoue sans effet de bord destructeur (upsert par {@code instanceId}).</p>
 */
public interface DiscoveryPort {

    void register(ServiceInstanceRegistration registration);

    /** A appeler explicitement avant l'arret du deploiement — voir 09.3, ordre d'arret gracieux. */
    void deregister(ServiceId id, String instanceId);

    /** Ne retourne QUE les instances saines. */
    ResolvedInstances resolve(ServiceId id);
}
