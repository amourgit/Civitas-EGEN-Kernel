package africa.civitas.egen.api.dto;

import java.util.List;

public class ExecutionPolicyDto {
    public String globalTimeout;
    public List<String> manualApprovalSteps;
    public Integer maxParallelSteps;
}
