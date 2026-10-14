package africa.civitas.egen.sdk;

/** Le circuit du service cible est ouvert : appel rejete SANS aucun acces reseau (docs/architecture/12). */
public final class CircuitOpenException extends EgenException {
    private static final long serialVersionUID = 1L;

    private final String service;

    public CircuitOpenException(String service) {
        super("Circuit ouvert pour le service " + service + " : appel rejete sans acces reseau");
        this.service = service;
    }

    public String service() {
        return service;
    }
}
