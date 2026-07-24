package africa.civitas.egen.kernel.authorization.exception;

/** Levee quand l'octroi cible d'une revocation est introuvable ou deja revoque. */
public class OctroiIntrouvableException extends RuntimeException {

    public OctroiIntrouvableException(String message) {
        super(message);
    }
}
