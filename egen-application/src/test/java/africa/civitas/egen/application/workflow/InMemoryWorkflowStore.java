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
public final class InMemoryWorkflowStore implements WorkflowStorePort {

    private record Header(WorkflowId workflowId, WorkflowExecutionStatus status, java.time.Instant startedAt,
                          Map<String, String> input) {
    }

    private final Map<String, Header> headers = new LinkedHashMap<>();
    private final Map<String, List<StepResult>> results = new LinkedHashMap<>();
    private final Map<String, WorkflowDefinition> definitions = new LinkedHashMap<>();
    int droppedConflictingResults;

    private record LeaseEntry(String driverId, java.time.Instant expiresAt) {
    }

    private final Map<String, LeaseEntry> leases = new LinkedHashMap<>();

    /** Meme regle que PostgresWorkflowStoreAdapter : libre, expire (expiration atteinte) ou deja a ce pilote. */
    @Override
    public synchronized boolean tryClaim(ExecutionId id, String driverId, java.time.Instant now,
                                         java.time.Instant leaseExpiresAt) {
        LeaseEntry current = leases.get(id.value());
        if (current == null || !current.expiresAt().isAfter(now) || current.driverId().equals(driverId)) {
            leases.put(id.value(), new LeaseEntry(driverId, leaseExpiresAt));
            return true;
        }
        return false;
    }

    /** Seul le detenteur dont le bail n'a pas expire peut le prolonger. */
    @Override
    public synchronized boolean renew(ExecutionId id, String driverId, java.time.Instant now,
                                      java.time.Instant leaseExpiresAt) {
        LeaseEntry current = leases.get(id.value());
        if (current == null || !current.driverId().equals(driverId) || !current.expiresAt().isAfter(now)) {
            return false;
        }
        leases.put(id.value(), new LeaseEntry(driverId, leaseExpiresAt));
        return true;
    }

    @Override
    public synchronized void release(ExecutionId id, String driverId) {
        LeaseEntry current = leases.get(id.value());
        if (current != null && current.driverId().equals(driverId)) {
            leases.remove(id.value());
        }
    }

    /** Pilote qui detient le bail (expire ou non), vide si aucun : pour les tests. */
    synchronized Optional<String> leaseHolder(ExecutionId id) {
        return Optional.ofNullable(leases.get(id.value())).map(LeaseEntry::driverId);
    }

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
