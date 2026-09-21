package africa.civitas.egen.application.port;

/**
 * Erreur remontee par un {@link RegistryStorePort} — jamais une exception
 * non categorisee de son moteur de stockage sous-jacent (voir
 * docs/architecture/07-ports-et-adapters.md, meme principe que
 * {@link DeploymentException}/{@link DiscoveryException}).
 */
public class RegistryException extends RuntimeException {

    public RegistryException(String message) {
        super(message);
    }

    public RegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
