package africa.civitas.egen.sdk;

/** Erreur du SDK (plan de controle injoignable, reponse illisible, interruption...). */
public class EgenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EgenException(String message) {
        super(message);
    }

    public EgenException(String message, Throwable cause) {
        super(message, cause);
    }
}
