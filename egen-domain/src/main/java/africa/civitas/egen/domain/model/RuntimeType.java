package africa.civitas.egen.domain.model;

/**
 * Nature d'execution d'un service. Purement informatif pour le Kernel — voir
 * docs/architecture/06-service-manifest.md ("runtime.type") : EGEN n'uniformise
 * jamais la technologie des services, il se contente de savoir comment les
 * confier au Deployment Adapter concerne.
 */
public enum RuntimeType {
    CONTAINER,
    BINARY,
    EXTERNAL,
    VM
}
