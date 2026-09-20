package africa.civitas.egen.api.dto;

import java.util.List;

public class ServiceStatusDto {
    public String phase;
    public List<ConditionDto> conditions;
    public long observedGeneration;
}
