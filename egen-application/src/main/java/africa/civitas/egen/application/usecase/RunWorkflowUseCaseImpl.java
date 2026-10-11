package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.WorkflowStorePort;
import africa.civitas.egen.application.workflow.ExecutionNotFoundException;
import africa.civitas.egen.application.workflow.WorkflowEnginePort;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowId;
import africa.civitas.egen.domain.workflow.WorkflowInput;

import java.util.Objects;

public final class RunWorkflowUseCaseImpl implements RunWorkflowUseCase {

    private final WorkflowStorePort workflowStorePort;
    private final WorkflowEnginePort engine;

    public RunWorkflowUseCaseImpl(WorkflowStorePort workflowStorePort, WorkflowEnginePort engine) {
        this.workflowStorePort = Objects.requireNonNull(workflowStorePort, "workflowStorePort");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public ExecutionId run(WorkflowId workflowId, WorkflowInput input) {
        WorkflowDefinition definition = workflowStorePort.findDefinition(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        return engine.start(definition, input == null ? WorkflowInput.empty() : input);
    }

    @Override
    public WorkflowExecution observe(ExecutionId id) {
        return workflowStorePort.findById(id).orElseThrow(() -> new ExecutionNotFoundException(id));
    }

    @Override
    public void pause(ExecutionId id) {
        engine.pause(id);
    }

    @Override
    public void resume(ExecutionId id) {
        engine.resume(id);
    }

    @Override
    public void terminate(ExecutionId id, String reason) {
        engine.terminate(id, reason == null || reason.isBlank() ? "arret demande via l'API" : reason);
    }
}
