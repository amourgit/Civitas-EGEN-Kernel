package africa.civitas.egen.application.port;

/**
 * Texte d'une exception pour un message d'erreur d'adapter (ecart I11, docs/architecture/07, « Erreurs reseau »).
 *
 * <p>Une exception reseau (par exemple {@code java.net.ConnectException}) n'a souvent pas de message :
 * {@link Throwable#getMessage()} renvoie {@code null}, et le message d'adapter se terminait par « : null ».
 * Cette classe renvoie le message s'il existe, sinon le nom complet de la classe de l'exception, suivi de la cause
 * (jusqu'a {@value #MAX_DEPTH} niveaux) quand elle en a une. Elle ne renvoie jamais {@code null}.</p>
 */
public final class ErrorText {

    /** Nombre maximal de causes suivies quand les messages sont vides (evite toute boucle). */
    public static final int MAX_DEPTH = 3;

    private ErrorText() {
    }

    public static String of(Throwable error) {
        if (error == null) {
            return "cause inconnue";
        }
        return of(error, 0);
    }

    private static String of(Throwable error, int depth) {
        String message = error.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        String name = error.getClass().getName();
        Throwable cause = error.getCause();
        if (cause == null || cause == error || depth >= MAX_DEPTH) {
            return name;
        }
        return name + " (cause : " + of(cause, depth + 1) + ")";
    }
}
