package africa.civitas.egen.testkit;

import africa.civitas.egen.application.port.WorkflowStorePort;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Double en memoire de {@link WorkflowStorePort} pour les tests de niveau
 * module (voir docs/architecture/17-strategie-de-tests.md). Ne reproduit
 * pas la cle d'unicite (etape, tentative) du vrai store — ce contrat-la est
 * verifie contre PostgreSQL (PostgresWorkflowStoreAdapterIT) et par le
 * double de egen-application (WorkflowEngineTest).
 */
public final class InMemoryWorkflowStore implements WorkflowStorePort {

    private final Map<String, WorkflowExecution> executions = new ConcurrentHashMap<>();
    private final Map<String, WorkflowDefinition> definitions = new ConcurrentHashMap<>();

    @Override
    public WorkflowExecution save(WorkflowExecution execution) {
        executions.put(execution.executionId().value(), execution);
        return execution;
    }

    @Override
    public Optional<WorkflowExecution> findById(ExecutionId id) {
        return Optional.ofNullable(executions.get(id.value()));
    }

    @Override
    public List<ExecutionId> findRunningExecutions() {
        return executions.values().stream()
                .filter(e -> e.status() == WorkflowExecutionStatus.RUNNING
                        || e.status() == WorkflowExecutionStatus.COMPENSATING)
                .map(WorkflowExecution::executionId)
                .toList();
    }

    @Override
    public Optional<WorkflowDefinition> findDefinition(WorkflowId id) {
        return Optional.ofNullable(definitions.get(id.value()));
    }

    @Override
    public WorkflowDefinition saveDefinition(WorkflowDefinition definition) {
        definitions.put(definition.id().value(), definition);
        return definition;
    }
}
