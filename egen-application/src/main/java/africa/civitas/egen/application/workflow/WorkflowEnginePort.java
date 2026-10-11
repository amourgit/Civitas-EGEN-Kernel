package africa.civitas.egen.application.workflow;

import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowInput;

/**
 * Port du Moteur de Workflow (voir docs/architecture/11-moteur-de-workflow.md,
 * "Le port" et ses precisions d'implementation : {@code start} renvoie un
 * {@link ExecutionId}, {@code raiseEvent} n'est pas encore livre).
 */
public interface WorkflowEnginePort {

    /**
     * Enregistre la definition, cree l'execution (statut RUNNING) puis la
     * pilote ; ne bloque pas jusqu'a la fin de l'execution.
     */
    ExecutionId start(WorkflowDefinition definition, WorkflowInput input);

    /** RUNNING -> PAUSED ; prend effet entre deux etapes. Idempotent sur PAUSED. */
    void pause(ExecutionId id);

    /**
     * PAUSED -> RUNNING et relance du pilotage. Pour une etape soumise a
     * approbation manuelle, vaut approbation. Sans effet sur une execution
     * deja RUNNING/COMPENSATING.
     *
     * @throws africa.civitas.egen.domain.workflow.IllegalExecutionTransitionException
     *         si l'execution est deja terminee
     */
    void resume(ExecutionId id);

    /**
     * Arrete l'execution en annulant (Saga) les etapes deja reussies qui
     * declarent une compensation. Idempotent sur une execution terminee.
     */
    void terminate(ExecutionId id, String reason);

    /** @throws ExecutionNotFoundException si l'execution est inconnue */
    WorkflowExecutionStatus status(ExecutionId id);
}
