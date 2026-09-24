package africa.civitas.egen.application.port;

/**
 * DTO neutre representant l'etat observe d'une souscription — alimente
 * MessagingObservation (voir docs/architecture/07-ports-et-adapters.md,
 * "getBindingStatus").
 */
public record BindingStatus(long pendingMessages, long unacknowledgedMessages,
                             long redeliveredMessages) {

    public static BindingStatus empty() {
        return new BindingStatus(0, 0, 0);
    }
}
