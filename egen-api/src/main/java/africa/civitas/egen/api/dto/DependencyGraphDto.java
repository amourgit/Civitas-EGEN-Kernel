package africa.civitas.egen.api.dto;

import java.util.List;
import java.util.Map;

public class DependencyGraphDto {
    public List<String> services;
    public Map<String, List<String>> dependsOn;
    public List<String> topologicalOrder;

    public DependencyGraphDto(List<String> services, Map<String, List<String>> dependsOn,
                               List<String> topologicalOrder) {
        this.services = services;
        this.dependsOn = dependsOn;
        this.topologicalOrder = topologicalOrder;
    }
}
