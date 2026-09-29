package africa.civitas.egen.domain.model;

/**
 * L'équipe propriétaire déclarée dans {@code metadata.team} du
 * {@link ServiceManifest} (voir docs/architecture/06-service-manifest.md,
 * schéma annoté — {@code team: content-platform}). Sert de base au RBAC
 * scoped par équipe de l'API de contrôle (voir
 * docs/architecture/13-api-et-contrats.md, "Sécurité de l'API" et
 * docs/architecture/14-securite.md, "RBAC de l'API de contrôle EGEN").
 *
 * <p>Champ additif et rétro-compatible (voir
 * docs/architecture/13-api-et-contrats.md, "Versionnage et
 * compatibilité") : un manifeste qui ne déclare pas d'équipe obtient
 * {@link #unassigned()} plutôt que de faire échouer la construction —
 * EGEN reste utilisable en développement sans IAM configuré (garde-fou
 * n4, docs/architecture/02-principes-fondamentaux.md, "Kernel vide").
 * {@link #isUnassigned()} permet à l'appelant (ici, l'autorisation côté
 * egen-api) de décider explicitement quoi faire d'un manifeste sans
 * équipe, plutôt que de le traiter silencieusement comme appartenant à
 * une équipe réelle nommée "unassigned".</p>
 */
public record OwnerTeam(String value) {

    private static final OwnerTeam UNASSIGNED = new OwnerTeam("__unassigned__");

    public OwnerTeam {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OwnerTeam.value ne peut pas etre vide");
        }
    }

    public static OwnerTeam of(String value) {
        return new OwnerTeam(value);
    }

    /** Sentinelle explicite — jamais une équipe métier réelle (voir la javadoc de la classe). */
    public static OwnerTeam unassigned() {
        return UNASSIGNED;
    }

    public boolean isUnassigned() {
        return this.equals(UNASSIGNED);
    }
}
