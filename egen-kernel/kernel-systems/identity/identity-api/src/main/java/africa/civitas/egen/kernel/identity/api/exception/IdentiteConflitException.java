package africa.civitas.egen.kernel.identity.api.exception;

/**
 * Levee lorsqu'une commande de creation entre en conflit avec une contrainte
 * d'unicite existante (identifiant civil de reference, identifiant Keycloak,
 * identifiant de connexion...). Fait partie du contrat public : tout appelant de
 * identity-api doit pouvoir capturer cette exception sans dependre de identity-impl.
 */
public class IdentiteConflitException extends RuntimeException {

    public IdentiteConflitException(String message) {
        super(message);
    }
}
