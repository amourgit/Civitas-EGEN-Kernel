package africa.civitas.egen.application.observability;

import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.WorkflowStorePort;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowId;

import java.util.List;
import java.util.Optional;

/**
 * Decore un {@link WorkflowStorePort} concret des metriques generiques
 * d'appel d'adapter — voir {@link ObservedDeploymentPort}.
 */
public final class ObservedWorkflowStorePort implements WorkflowStorePort {

    private final WorkflowStorePort delegate;
    private final AdapterCallInstrumentation instrumentation;

    public ObservedWorkflowStorePort(WorkflowStorePort delegate, ObservabilityPort observabilityPort,
                                      String adapterName) {
        this.delegate = delegate;
        this.instrumentation = new AdapterCallInstrumentation(observabilityPort, adapterName);
    }

    @Override
    public WorkflowExecution save(WorkflowExecution execution) {
        return instrumentation.call("workflow.save", () -> delegate.save(execution));
    }

    @Override
    public Optional<WorkflowExecution> findById(ExecutionId id) {
        return instrumentation.call("workflow.findById", () -> delegate.findById(id));
    }

    @Override
    public List<ExecutionId> findRunningExecutions() {
        return instrumentation.call("workflow.findRunningExecutions", delegate::findRunningExecutions);
    }

    @Override
    public Optional<WorkflowDefinition> findDefinition(WorkflowId id) {
        return instrumentation.call("workflow.findDefinition", () -> delegate.findDefinition(id));
    }

    @Override
    public WorkflowDefinition saveDefinition(WorkflowDefinition definition) {
        return instrumentation.call("workflow.saveDefinition", () -> delegate.saveDefinition(definition));
    }
}
