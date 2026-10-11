package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.workflow.ExecutionNotFoundException;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowId;
import africa.civitas.egen.domain.workflow.WorkflowInput;

/**
 * Port primaire — declenchement et suivi d'executions (voir
 * docs/architecture/13-api-et-contrats.md,
 * {@code /api/v1/workflows/{id}/executions}). Comme "Declare" et "Stop",
 * {@link #run} ne fait jamais attendre la fin du workflow : il renvoie
 * l'{@link ExecutionId} que l'API expose en {@code 202 Accepted} +
 * {@code Location}, et le suivi se fait par {@link #observe}.
 */
public interface RunWorkflowUseCase {

    /** @throws WorkflowNotFoundException si la definition n'a jamais ete declaree */
    ExecutionId run(WorkflowId workflowId, WorkflowInput input);

    /** @throws ExecutionNotFoundException si l'execution est inconnue */
    WorkflowExecution observe(ExecutionId id);

    /** Voir {@link africa.civitas.egen.application.workflow.WorkflowEnginePort#pause}. */
    void pause(ExecutionId id);

    /** Voir {@link africa.civitas.egen.application.workflow.WorkflowEnginePort#resume}. */
    void resume(ExecutionId id);

    /** Voir {@link africa.civitas.egen.application.workflow.WorkflowEnginePort#terminate}. */
    void terminate(ExecutionId id, String reason);
}
