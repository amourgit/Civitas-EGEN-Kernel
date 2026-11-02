package africa.civitas.egen.application.usecase;

/** Le store de configuration est injoignable : rien n'a ete publie (ou lu) — l'API repond 503. */
public final class ConfigurationStoreUnavailableException extends RuntimeException {

    public ConfigurationStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
