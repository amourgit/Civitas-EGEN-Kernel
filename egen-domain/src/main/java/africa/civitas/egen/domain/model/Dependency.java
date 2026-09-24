package africa.civitas.egen.domain.model;

/**
 * Dependance technique declaree d'un service envers un autre (voir
 * docs/architecture/06-service-manifest.md, "dependencies.services").
 * EGEN ne connait jamais la raison metier d'une dependance — uniquement le
 * fait technique (voir docs/architecture/10-gestion-des-dependances.md,
 * "Ce qu'EGEN ne fait jamais sur les dependances").
 */
public record Dependency(ServiceId serviceId, String versionConstraint, boolean required) {

    public Dependency {
        if (serviceId == null) {
            throw new IllegalArgumentException("Dependency.serviceId est obligatoire");
        }
        if (versionConstraint == null || versionConstraint.isBlank()) {
            throw new IllegalArgumentException("Dependency.versionConstraint ne peut pas etre vide");
        }
    }
}
