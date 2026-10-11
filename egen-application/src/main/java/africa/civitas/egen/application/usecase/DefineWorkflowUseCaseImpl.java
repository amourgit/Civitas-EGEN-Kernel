package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.WorkflowStorePort;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowId;

import java.util.Objects;

public final class DefineWorkflowUseCaseImpl implements DefineWorkflowUseCase {

    private final WorkflowStorePort workflowStorePort;

    public DefineWorkflowUseCaseImpl(WorkflowStorePort workflowStorePort) {
        this.workflowStorePort = Objects.requireNonNull(workflowStorePort, "workflowStorePort");
    }

    @Override
    public WorkflowDefinition define(WorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return workflowStorePort.saveDefinition(definition);
    }

    @Override
    public WorkflowDefinition get(WorkflowId id) {
        return workflowStorePort.findDefinition(id).orElseThrow(() -> new WorkflowNotFoundException(id));
    }
}
