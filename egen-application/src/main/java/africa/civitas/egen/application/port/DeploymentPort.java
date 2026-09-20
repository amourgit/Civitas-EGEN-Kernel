package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceVersion;

import java.util.List;

/**
 * Port secondaire — ce que le domaine exige de tout moteur de deploiement
 * (Nomad en V1, voir docs/architecture/07-ports-et-adapters.md). Defini ici,
 * implemente par un module egen-adapters/* concret (ex.
 * egen-adapter-nomad.NomadDeploymentAdapter).
 *
 * <p><b>Chaque methode mutative doit etre idempotente</b> — rejouable sans
 * effet de bord destructeur (garde-fou n7,
 * docs/architecture/02-principes-fondamentaux.md), condition necessaire a la
 * boucle de reconciliation level-triggered
 * (docs/architecture/04-moteur-de-reconciliation.md).</p>
 */
public interface DeploymentPort {

    /**
     * Cree ou met a jour (upsert) le deploiement du service {@code id} selon
     * {@code spec}. Idempotent : appeler create() deux fois de suite avec la
     * meme spec ne doit produire aucune erreur ni duplication.
     */
    DeploymentHandle create(ServiceId id, DeploymentSpec spec);

    /** Met a jour un deploiement existant (rolling update selon la strategie du moteur). */
    void update(ServiceId id, DeploymentSpec newSpec);

    /** Ajuste le nombre de replicas desirees. */
    void scale(ServiceId id, int replicas);

    /** Force une nouvelle evaluation/redemarrage sans changer la spec. */
    void restart(ServiceId id);

    /** Arrete le deploiement sans le supprimer (conserve l'historique pour audit). */
    void stop(ServiceId id);

    /** Supprime definitivement le deploiement (purge). A utiliser uniquement depuis la phase REMOVING. */
    void remove(ServiceId id);

    /** Lecture — jamais mutatif. Reconstruit l'etat observe courant. */
    DeploymentObservation getStatus(ServiceId id);

    /** Detail des instances en cours d'execution. */
    List<AllocationInfo> listAllocations(ServiceId id);

    /** Revient a une version precedemment deployee. */
    void rollback(ServiceId id, ServiceVersion targetVersion);
}
