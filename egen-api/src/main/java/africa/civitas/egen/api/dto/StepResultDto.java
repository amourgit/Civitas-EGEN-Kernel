package africa.civitas.egen.api.dto;

import java.util.Map;

public class StepResultDto {
    public String stepId;
    public String status;
    public int attempt;
    public String startedAt;
    public String completedAt;
    public Map<String, String> output;
    public String error;
}
