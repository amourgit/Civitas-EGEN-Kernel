package africa.civitas.egen.sdk;

/**
 * Toutes les tentatives ont echoue SANS obtenir de reponse HTTP (delai
 * depasse ou erreur reseau). Quand une reponse existe — meme un 503 — elle
 * est renvoyee telle quelle, jamais enveloppee dans une exception.
 */
public final class CallFailedException extends EgenException {
    private static final long serialVersionUID = 1L;

    private final String service;
    private final String reason;
    private final int attempts;

    public CallFailedException(String service, String reason, int attempts) {
        super("Appel de " + service + " en echec apres " + attempts + " tentative(s) : " + reason);
        this.service = service;
        this.reason = reason;
        this.attempts = attempts;
    }

    public String service() {
        return service;
    }

    /** {@code "timeout"} ou {@code "network"}. */
    public String reason() {
        return reason;
    }

    public int attempts() {
        return attempts;
    }
}
