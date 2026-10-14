package africa.civitas.egen.api.dto;

import java.util.List;

public class DiscoverResponseDto {
    public String serviceId;
    public List<ResolvedInstanceDto> instances;
    public ResiliencyViewDto resiliency;

    public DiscoverResponseDto(String serviceId, List<ResolvedInstanceDto> instances, ResiliencyViewDto resiliency) {
        this.serviceId = serviceId;
        this.instances = instances;
        this.resiliency = resiliency;
    }
}
