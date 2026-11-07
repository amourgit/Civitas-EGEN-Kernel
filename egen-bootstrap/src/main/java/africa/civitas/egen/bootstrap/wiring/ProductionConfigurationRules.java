package africa.civitas.egen.bootstrap.wiring;

/**
 * Règles de configuration que le Kernel impose <b>en production seulement</b> (voir
 * docs/architecture/14-securite.md, « Ce que le Kernel ne fait jamais », et le constat K6 de
 * docs/architecture/24-revue-de-securite-externe.md). Classe volontairement sans aucun import Quarkus ni CDI :
 * les règles se testent sans conteneur.
 */
final class ProductionConfigurationRules {

    /** Le mot de passe que le développement local utilisait par défaut ; il ne doit jamais atteindre la production. */
    static final String HISTORICAL_DEFAULT_DATABASE_PASSWORD = "egen";

    static final String DATABASE_PASSWORD_VARIABLE = "EGEN_REGISTRY_DB_PASSWORD";

    private ProductionConfigurationRules() {
    }

    /**
     * @throws IllegalStateException si le mot de passe de la base du Registry est absent, vide, ou égal à la valeur par
     *                               défaut du développement : le démarrage doit échouer, avec un message qui dit quoi faire
     */
    static void assertDatabasePasswordIsExplicit(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Démarrage refusé en production : le mot de passe de la base du Registry est "
                    + "absent. Définir la variable d'environnement " + DATABASE_PASSWORD_VARIABLE
                    + " (aucune valeur par défaut n'existe en production).");
        }
        if (HISTORICAL_DEFAULT_DATABASE_PASSWORD.equals(password)) {
            throw new IllegalStateException("Démarrage refusé en production : le mot de passe de la base du Registry est "
                    + "la valeur par défaut du développement. Définir " + DATABASE_PASSWORD_VARIABLE
                    + " avec un secret propre à cet environnement.");
        }
    }
}
