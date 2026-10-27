package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.event.TechnicalEvent;
import africa.civitas.egen.domain.model.OwnerTeam;

import java.util.function.Predicate;

/**
 * Port primaire — publication d'un evenement technique pour un appelant qui n'a pas d'acces direct au
 * broker (voir docs/architecture/12-communication-fabric.md, "Publication d'evenements par le SDK", et
 * 13-api-et-contrats.md, {@code POST /api/v1/events/publish}).
 *
 * <p>Regle : un appelant ne publie que ce qu'un manifeste annonce. Le type doit figurer dans
 * {@code events.publishes} d'au moins un service declare, et l'appelant doit etre autorise sur l'un de ces
 * services (meme regle d'equipe que {@code Declare}, voir 14-securite.md). Le Kernel relaie vers le
 * Messaging Port sans jamais lire {@code data}.</p>
 */
public interface PublishEventUseCase {

    /**
     * @param callerMayActFor dit si l'appelant est autorise pour l'equipe proprietaire d'un service
     *                        publieur (la politique d'equipe vit cote API, jamais ici)
     * @throws EventPublicationNotAllowedException type publie par aucun service declare, ou appelant
     *         autorise sur aucun de ses publieurs
     * @throws africa.civitas.egen.application.port.MessagingException broker injoignable ou refus
     */
    TechnicalEvent publish(PublishEventCommand command, Predicate<OwnerTeam> callerMayActFor);
}
