package africa.civitas.egen.sdk;

/** Aucune instance saine n'est resolue pour ce service — aucun appel n'a ete tente. */
public final class NoInstanceException extends EgenException {
    private static final long serialVersionUID = 1L;

    private final String service;

    public NoInstanceException(String service) {
        super("Aucune instance saine pour le service " + service);
        this.service = service;
    }

    public String service() {
        return service;
    }
}
