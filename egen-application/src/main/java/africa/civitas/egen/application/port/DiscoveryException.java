package africa.civitas.egen.application.port;

/**
 * Erreur remontee par un {@link DiscoveryPort} — jamais une exception non
 * categorisee de son SDK sous-jacent (voir docs/architecture/07-ports-et-adapters.md).
 */
public class DiscoveryException extends RuntimeException {

    public DiscoveryException(String message) {
        super(message);
    }

    public DiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
