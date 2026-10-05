package africa.civitas.egen.application.port;

/**
 * Erreur remontee par un {@link WorkflowStorePort} — jamais une exception
 * non categorisee de son moteur de stockage sous-jacent (meme principe que
 * {@link RegistryException}).
 */
public class WorkflowStoreException extends RuntimeException {

    public WorkflowStoreException(String message) {
        super(message);
    }

    public WorkflowStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
