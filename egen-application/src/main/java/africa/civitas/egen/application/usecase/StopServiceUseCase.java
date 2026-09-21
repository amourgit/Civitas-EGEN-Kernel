package africa.civitas.egen.application.usecase;

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
}
