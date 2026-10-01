package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.domain.model.ServiceId;

/**
 * Port primaire — demande explicite d'arret gracieux (voir
 * docs/architecture/09-cycle-de-vie.md, "RUNNING/DEGRADED/FAILED ->
 * STOPPING"). Comme pour "Declare", ne fait jamais attendre l'arret reel :
 * il fait passer le service en STOPPING et laisse la boucle de
 * reconciliation executer l'ordre precis deregister -> grace period ->
 * stop() (voir 09.3).
 *
 * @throws ServiceNotFoundException si aucun DesiredState n'a jamais ete
 *         declare pour cet identifiant
 */
public interface StopServiceUseCase {

    void stop(ServiceId id);

    /**
     * Variante propageant le contexte de trace de l'appel HTTP entrant
     * jusqu'au premier appel reseau (deregistrement Discovery) du cycle de
     * reconciliation qui traite cet arret — voir
     * docs/architecture/15-observabilite.md et
     * africa.civitas.egen.application.reconciliation.PendingDeclareTraces.
     * Par defaut, delegue simplement a {@link #stop(ServiceId)}.
     */
    default void stop(ServiceId id, TraceContext triggeringTrace) {
        stop(id);
    }
}
