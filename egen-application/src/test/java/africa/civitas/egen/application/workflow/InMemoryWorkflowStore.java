package africa.civitas.egen.application.workflow;

import africa.civitas.egen.application.port.WorkflowStorePort;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.StepResult;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Double fidele au contrat de PostgresWorkflowStoreAdapter : statut upsert,
 * entree immuable, resultats insert-only avec cle (etape, tentative) — un
 * fait different porte par une cle deja existante est SILENCIEUSEMENT perdu
 * en base ({@code ON CONFLICT DO NOTHING}) ; ce double le compte dans
 * {@link #droppedConflictingResults} pour que les tests le detectent.
 */
final class InMemoryWorkflowStore implements WorkflowStorePort {

    private record Header(WorkflowId workflowId, WorkflowExecutionStatus status, java.time.Instant startedAt,
                          Map<String, String> input) {
    }

    private final Map<String, Header> headers = new LinkedHashMap<>();
    private final Map<String, List<StepResult>> results = new LinkedHashMap<>();
    private final Map<String, WorkflowDefinition> definitions = new LinkedHashMap<>();
    int droppedConflictingResults;

    @Override
    public synchronized WorkflowExecution save(WorkflowExecution execution) {
        String id = execution.executionId().value();
        Header previous = headers.get(id);
        headers.put(id, new Header(execution.workflowId(), execution.status(), execution.startedAt(),
                previous == null ? execution.input() : previous.input()));
        List<StepResult> persisted = results.computeIfAbsent(id, k -> new ArrayList<>());
        for (StepResult candidate : execution.stepResults()) {
            Optional<StepResult> sameKey = persisted.stream()
                    .filter(r -> r.stepId().equals(candidate.stepId()) && r.attempt() == candidate.attempt())
                    .findFirst();
            if (sameKey.isEmpty()) {
                persisted.add(candidate);
            } else if (!sameKey.get().equals(candidate)) {
                droppedConflictingResults++;
            }
        }
        return execution;
    }

    @Override
    public synchronized Optional<WorkflowExecution> findById(ExecutionId id) {
        Header header = headers.get(id.value());
        if (header == null) {
            return Optional.empty();
        }
        return Optional.of(WorkflowExecution.rehydrate(header.workflowId(), id, header.status(),
                List.copyOf(results.get(id.value())), header.startedAt(), header.input()));
    }

    @Override
    public synchronized List<ExecutionId> findRunningExecutions() {
        return headers.entrySet().stream()
                .filter(e -> e.getValue().status() == WorkflowExecutionStatus.RUNNING
                        || e.getValue().status() == WorkflowExecutionStatus.COMPENSATING)
                .map(e -> ExecutionId.of(e.getKey()))
                .toList();
    }

    @Override
    public synchronized Optional<WorkflowDefinition> findDefinition(WorkflowId id) {
        return Optional.ofNullable(definitions.get(id.value()));
    }

    @Override
    public synchronized WorkflowDefinition saveDefinition(WorkflowDefinition definition) {
        definitions.put(definition.id().value(), definition);
        return definition;
    }

    synchronized void deleteDefinition(WorkflowId id) {
        definitions.remove(id.value());
    }

    synchronized List<ExecutionId> allExecutionIds() {
        return headers.keySet().stream().map(ExecutionId::of).toList();
    }
}
