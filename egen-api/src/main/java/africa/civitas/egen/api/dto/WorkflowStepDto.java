package africa.civitas.egen.api.dto;

import java.util.List;

public class WorkflowStepDto {
    public String id;
    public String service;
    public String operation;
    public List<String> dependsOn;
    public String timeout;
    public StepRetryDto retry;
    public StepCompensationDto compensation;
}
