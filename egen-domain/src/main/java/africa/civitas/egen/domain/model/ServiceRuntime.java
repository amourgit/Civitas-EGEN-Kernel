package africa.civitas.egen.domain.model;

/**
 * Ce que le service EST (voir docs/architecture/06-service-manifest.md,
 * section "runtime"). Le champ {@code language} est informatif uniquement — le
 * Kernel ne l'interprete jamais.
 */
public record ServiceRuntime(RuntimeType type, String artifact, String language) {

    public ServiceRuntime {
        if (type == null) {
            throw new IllegalArgumentException("ServiceRuntime.type est obligatoire");
        }
        if (artifact == null || artifact.isBlank()) {
            throw new IllegalArgumentException("ServiceRuntime.artifact ne peut pas etre vide");
        }
    }
}
