package africa.civitas.egen.application.usecase;

/** Publication refusee : type que aucun service declare n'annonce, ou appelant non autorise sur ses publieurs. */
public final class EventPublicationNotAllowedException extends RuntimeException {

    public EventPublicationNotAllowedException(String message) {
        super(message);
    }
}
