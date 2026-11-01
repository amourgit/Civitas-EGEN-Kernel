package africa.civitas.egen.domain.model;

/**
 * Dependance technique declaree d'un service envers un autre (voir
 * docs/architecture/06-service-manifest.md, "dependencies.services").
 * EGEN ne connait jamais la raison metier d'une dependance — uniquement le
 * fait technique (voir docs/architecture/10-gestion-des-dependances.md,
 * "Ce qu'EGEN ne fait jamais sur les dependances").
 *
 * <p>{@code versionConstraint} est conservee telle que declaree (restituee a l'identique par
 * l'API) mais doit etre lisible : voir {@link VersionConstraint}. Une contrainte illisible rend
 * le manifeste invalide, refuse a {@code Declare} plutot qu'ignore en silence (06,
 * « Validation du manifeste »).</p>
 */
public record Dependency(ServiceId serviceId, String versionConstraint, boolean required) {

    public Dependency {
        if (serviceId == null) {
            throw new IllegalArgumentException("Dependency.serviceId est obligatoire");
        }
        if (versionConstraint == null || versionConstraint.isBlank()) {
            throw new IllegalArgumentException("Dependency.versionConstraint ne peut pas etre vide");
        }
        VersionConstraint.parse(versionConstraint); // refuse une contrainte illisible
    }

    /** La contrainte de version, analysee. */
    public VersionConstraint constraint() {
        return VersionConstraint.parse(versionConstraint);
    }
}
