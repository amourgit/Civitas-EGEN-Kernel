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
}
