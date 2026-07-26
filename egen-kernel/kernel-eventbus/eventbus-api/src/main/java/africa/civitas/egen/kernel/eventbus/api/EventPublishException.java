package africa.civitas.egen.kernel.eventbus.api;

/**
 * Levee quand la publication elle-meme echoue au niveau du transport (courtier
 * injoignable, timeout...). Ne concerne jamais l'execution d'un gestionnaire cote
 * souscripteur — voir {@link EventHandler}, dont les exceptions sont toujours
 * isolees, jamais remontees a l'emetteur.
 */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message) {
        super(message);
    }

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
