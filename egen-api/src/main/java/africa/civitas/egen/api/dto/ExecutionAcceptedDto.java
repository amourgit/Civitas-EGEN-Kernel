package africa.civitas.egen.api.dto;

public class ExecutionAcceptedDto {
    public String executionId;
    public String statusUrl;

    public ExecutionAcceptedDto(String executionId, String statusUrl) {
        this.executionId = executionId;
        this.statusUrl = statusUrl;
    }
}
