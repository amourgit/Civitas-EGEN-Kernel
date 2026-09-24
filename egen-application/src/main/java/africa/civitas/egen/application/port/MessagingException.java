package africa.civitas.egen.application.port;

/**
 * Erreur remontee par un {@link MessagingPort} — jamais une exception non
 * categorisee de son SDK sous-jacent (meme principe que
 * {@link DeploymentException}/{@link DiscoveryException}).
 */
public class MessagingException extends RuntimeException {

    public MessagingException(String message) {
        super(message);
    }

    public MessagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
