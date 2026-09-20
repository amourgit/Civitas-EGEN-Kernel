package africa.civitas.egen.domain.model;

/**
 * Environnement cible d'un DesiredState (ex. "production", "staging"). Voir
 * docs/architecture/05-modele-de-domaine.md.
 */
public record TargetEnvironment(String name) {

    public TargetEnvironment {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("TargetEnvironment.name ne peut pas etre vide");
        }
    }

    public static TargetEnvironment of(String name) {
        return new TargetEnvironment(name);
    }
}
