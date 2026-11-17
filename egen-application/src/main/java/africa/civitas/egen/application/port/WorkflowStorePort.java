package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowId;

import java.util.List;
import java.util.Optional;

/**
 * Port secondaire — voir docs/architecture/11-moteur-de-workflow.md,
 * "Durabilite de l'execution" pour le raisonnement complet.
 */
public interface WorkflowStorePort {

    /** Upsert — persiste l'etat COURANT (deja enrichi, voir {@code WorkflowExecution.withStepResult}). */
    WorkflowExecution save(WorkflowExecution execution);

    Optional<WorkflowExecution> findById(ExecutionId id);

    /** Reprise au demarrage du Kernel — meme role que {@code RegistryStorePort.findAllIds()} pour les services. */
    List<ExecutionId> findRunningExecutions();

    Optional<WorkflowDefinition> findDefinition(WorkflowId id);

    WorkflowDefinition saveDefinition(WorkflowDefinition definition);

    // ------------------------------------------------------------------
    // Bail de pilotage (phase P0, etape P0-3b ; docs/architecture/11, « Bail d'execution »)
    // ------------------------------------------------------------------
    // Un seul Kernel pilote une execution a la fois, meme avec plusieurs exemplaires. Implementation par defaut :
    // aucune coordination (un seul processus) ; l'adapter PostgreSQL les redefinit, en base.

    /**
     * Prend le bail d'une execution pour {@code driverId}.
     *
     * @return {@code true} si le bail etait libre, expire ou deja a ce pilote ; {@code false} si un autre pilote le
     *         detient encore (il ne faut alors PAS piloter l'execution)
     */
    default boolean tryClaim(ExecutionId id, String driverId, java.time.Instant now, java.time.Instant leaseExpiresAt) {
        return true;
    }

    /**
     * Prolonge le bail.
     *
     * @return {@code false} si le pilote ne le detient plus (expire ou repris par un autre) : il doit cesser de
     *         piloter l'execution
     */
    default boolean renew(ExecutionId id, String driverId, java.time.Instant now, java.time.Instant leaseExpiresAt) {
        return true;
    }

    /** Rend le bail s'il est detenu par ce pilote (sans effet sinon). */
    default void release(ExecutionId id, String driverId) {
    }
}
