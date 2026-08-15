package africa.civitas.egen.kernel.moduleregistry.exception;

/** Leve quand une Souscription active existe deja pour ce Contexte et ce module. */
public class SouscriptionDejaActiveException extends RuntimeException {

    public SouscriptionDejaActiveException(String message) {
        super(message);
    }
}
