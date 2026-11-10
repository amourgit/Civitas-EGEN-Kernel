package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.event.TechnicalEvent;

/**
 * Port primaire — publication d'un evenement technique pour un appelant qui n'a pas d'acces direct au
 * broker (voir docs/architecture/12-communication-fabric.md, "Publication d'evenements par le SDK", et
 * 13-api-et-contrats.md, {@code POST /api/v1/events/publish}).
 *
 * <p>Regle : un appelant ne publie que ce qu'un manifeste annonce. Le type doit figurer dans
 * {@code events.publishes} d'au moins un service declare. Le droit d'appeler (administrateur) est verifie par
 * l'API avant d'arriver ici (voir 14-securite.md) : ce use case ne connait ni equipe ni role. Le Kernel relaie
 * vers le Messaging Port sans jamais lire {@code data}.</p>
 */
public interface PublishEventUseCase {

    /**
     * @throws EventPublicationNotAllowedException type publie par aucun service declare
     * @throws africa.civitas.egen.application.port.MessagingException broker injoignable ou refus
     */
    TechnicalEvent publish(PublishEventCommand command);
}
