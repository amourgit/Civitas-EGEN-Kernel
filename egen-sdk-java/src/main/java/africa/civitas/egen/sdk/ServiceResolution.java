package africa.civitas.egen.sdk;

import java.util.List;

/** Resultat de {@code GET /api/v1/discover/{service}} : instances saines ET politique de la cible. */
public record ServiceResolution(String serviceId, List<ServiceInstance> instances, ResiliencyPolicy resiliency) {

    public ServiceResolution {
        instances = List.copyOf(instances);
    }
}
