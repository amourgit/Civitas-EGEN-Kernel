package africa.civitas.egen.kernel.moduleregistry.exception;

/** Leve quand l'Activation ciblee par une desactivation est introuvable ou deja eteinte. */
public class ActivationIntrouvableException extends RuntimeException {

    public ActivationIntrouvableException(String message) {
        super(message);
    }
}
