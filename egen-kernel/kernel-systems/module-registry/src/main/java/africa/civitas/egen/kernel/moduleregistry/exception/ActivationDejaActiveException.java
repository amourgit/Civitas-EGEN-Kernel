package africa.civitas.egen.kernel.moduleregistry.exception;

/** Leve quand une Activation active existe deja pour cette Cellule et ce module. */
public class ActivationDejaActiveException extends RuntimeException {

    public ActivationDejaActiveException(String message) {
        super(message);
    }
}
