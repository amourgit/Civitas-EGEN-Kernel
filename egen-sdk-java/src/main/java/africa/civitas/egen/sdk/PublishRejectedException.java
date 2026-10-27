package africa.civitas.egen.sdk;

/**
 * Le Kernel a repondu et N'A PAS publie l'evenement (docs/architecture/12) : reponse definitive (400 corps
 * invalide, 401, 403 type non annonce ou appelant non autorise — jamais reessayee), ou echec reessayable
 * persistant une fois la politique appliquee (503 messagerie indisponible...). Contrairement a
 * {@code call}, {@code publish} ne renvoie jamais une reponse d'echec comme une valeur.
 */
public final class PublishRejectedException extends EgenException {
    private static final long serialVersionUID = 1L;

    private final int status;
    private final int attempts;

    public PublishRejectedException(int status, String message, int attempts) {
        super("Publication refusee par le Kernel (HTTP " + status + ") apres " + attempts
                + " tentative(s) : " + message);
        this.status = status;
        this.attempts = attempts;
    }

    public int status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }
}
