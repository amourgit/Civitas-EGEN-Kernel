package africa.civitas.egen.api.dto;

import java.util.List;
import java.util.Map;

public class WorkflowExecutionDto {
    public String executionId;
    public String workflowId;
    public String status;
    public String startedAt;
    public Map<String, String> input;
    public List<StepResultDto> stepResults;
}
