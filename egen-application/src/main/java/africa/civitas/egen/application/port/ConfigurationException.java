package africa.civitas.egen.application.port;

/** Erreur remontee par un {@link ConfigurationPort} — meme principe que les autres ports secondaires. */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
