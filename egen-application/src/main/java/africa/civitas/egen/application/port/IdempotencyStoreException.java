package africa.civitas.egen.application.port;

/** Le stockage des reponses rejouables est inaccessible (docs/architecture/13, « Idempotence »). */
public class IdempotencyStoreException extends RuntimeException {
    public IdempotencyStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
