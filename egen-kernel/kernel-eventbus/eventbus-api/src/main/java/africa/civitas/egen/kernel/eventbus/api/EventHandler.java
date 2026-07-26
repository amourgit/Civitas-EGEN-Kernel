package africa.civitas.egen.kernel.eventbus.api;

import africa.civitas.egen.kernel.sdk.event.EventEnvelope;

/**
 * Traite un evenement recu par souscription. Une exception levee ici n'interrompt
 * jamais la diffusion aux autres gestionnaires souscrits au meme evenement — voir
 * {@link EventBus#publier} : un gestionnaire fautif ne doit jamais faire tomber ni
 * l'emetteur, ni les autres souscripteurs. Voir {@link InMemoryEventBus} pour le
 * detail de cette isolation.
 *
 * @param <T> le type de la charge utile attendue — doit correspondre au type
 *            reellement publie pour l'{@link africa.civitas.egen.kernel.sdk.event.EventType}
 *            souscrit ; ce module ne peut pas le garantir a la compilation (erasure),
 *            seule la discipline de nommage des types d'evenements le garantit en
 *            pratique.
 */
@FunctionalInterface
public interface EventHandler<T> {

    void traiter(EventEnvelope<T> evenement);
}
