package africa.civitas.egen.api.dto;

import java.util.Map;

/** Corps (optionnel) de POST /api/v1/workflows/{id}/executions. */
public class RunWorkflowRequestDto {
    public Map<String, String> input;
}
