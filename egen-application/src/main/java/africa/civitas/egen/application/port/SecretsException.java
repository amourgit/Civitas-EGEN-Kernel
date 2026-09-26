package africa.civitas.egen.application.port;

/** Erreur remontee par un {@link SecretsPort} — meme principe que les autres ports secondaires. */
public class SecretsException extends RuntimeException {

    public SecretsException(String message) {
        super(message);
    }

    public SecretsException(String message, Throwable cause) {
        super(message, cause);
    }
}
