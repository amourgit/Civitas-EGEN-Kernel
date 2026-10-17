package africa.civitas.egen.api.dto;

/**
 * Lecture d'un manifeste (voir docs/architecture/13-api-et-contrats.md,
 * {@code GET /api/v1/services[/{id}]}) : le manifeste tel qu'il est declare,
 * avec la generation du {@code DesiredState} qui le porte.
 */
public class ServiceManifestResponseDto {
    public String serviceId;
    public long generation;
    public ServiceManifestDto manifest;

    public ServiceManifestResponseDto(String serviceId, long generation, ServiceManifestDto manifest) {
        this.serviceId = serviceId;
        this.generation = generation;
        this.manifest = manifest;
    }
}
