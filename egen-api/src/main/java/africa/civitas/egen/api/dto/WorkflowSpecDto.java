package africa.civitas.egen.api.dto;

import java.util.List;

public class WorkflowSpecDto {
    public List<WorkflowStepDto> steps;
    public String compensationStrategy;
    public ExecutionPolicyDto executionPolicy;
}
