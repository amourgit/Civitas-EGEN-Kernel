package africa.civitas.egen.domain.model;

/**
 * L'équipe déclarée dans {@code metadata.team} du {@link ServiceManifest} (voir
 * docs/architecture/06-service-manifest.md, schéma annoté — {@code team: content-platform}).
 *
 * <p><b>Valeur purement informative, pour la traçabilité</b> (décision de Samuel, 10 novembre 2026,
 * docs/architecture/14-securite.md) : elle est stockée et restituée telle quelle et n'est JAMAIS lue pour
 * décider — ni autorisation, ni filtre, ni propriété. Le nom {@code OwnerTeam} date de l'époque où elle servait au
 * RBAC par équipe ; il est conservé pour ne pas multiplier les changements sans compilateur (un renommage reste
 * possible).</p>
 *
 * <p>Champ additif et rétro-compatible (voir docs/architecture/13-api-et-contrats.md, "Versionnage et
 * compatibilité") : un manifeste qui ne déclare pas d'équipe obtient {@link #unassigned()} plutôt que de faire
 * échouer la construction — EGEN reste utilisable en développement sans IAM configuré (garde-fou n4,
 * docs/architecture/02-principes-fondamentaux.md, "Kernel vide"). {@link #isUnassigned()} distingue « aucune équipe
 * déclarée » d'une équipe réelle qui s'appellerait "unassigned".</p>
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
