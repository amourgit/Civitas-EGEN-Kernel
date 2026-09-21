package africa.civitas.egen.api.dto;

import java.util.List;

public class DiscoverResponseDto {
    public String serviceId;
    public List<ResolvedInstanceDto> instances;

    public DiscoverResponseDto(String serviceId, List<ResolvedInstanceDto> instances) {
        this.serviceId = serviceId;
        this.instances = instances;
    }
}
