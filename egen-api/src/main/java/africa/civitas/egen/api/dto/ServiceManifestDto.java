package africa.civitas.egen.api.dto;

/**
 * Transport JSON/YAML du EGEN Service Manifest (voir
 * docs/architecture/06-service-manifest.md). Champs Phase 1 uniquement —
 * network/health/dependencies/events/configuration/lifecycle rejoignent ce
 * DTO aux phases qui les introduisent (docs/architecture/19-feuille-de-route.md).
 * Jamais expose directement au domaine : voir
 * africa.civitas.egen.api.mapper.ServiceManifestMapper.
 */
public class ServiceManifestDto {
    public String apiVersion;
    public String kind;
    public MetadataDto metadata;
    public RuntimeDto runtime;
    public DeploymentDto deployment;
    public HealthDto health;
    public LifecycleDto lifecycle;
}
