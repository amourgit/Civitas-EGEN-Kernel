package africa.civitas.egen.sdk;

/**
 * Resultat d'UNE tentative : soit une reponse HTTP ({@code status}), soit une
 * erreur sans reponse ({@code error} = {@code "timeout"} ou {@code "network"}).
 * {@code response} porte la reponse complete quand elle existe.
 */
record Outcome(Integer status, String error, EgenResponse response) {

    static Outcome ofStatus(int status) {
        return new Outcome(status, null, null);
    }

    static Outcome ofResponse(EgenResponse response) {
        return new Outcome(response.status(), null, response);
    }

    static Outcome ofError(String error) {
        return new Outcome(null, error, null);
    }

    /** Echec reessayable : 5xx, 408, 429, delai depasse, erreur reseau (docs/architecture/12). */
    boolean isRetryableFailure() {
        return status == null || status >= 500 || status == 408 || status == 429;
    }

    /** Forme textuelle du contrat partage : status:N | error:timeout | error:network. */
    String render() {
        return status != null ? "status:" + status : "error:" + error;
    }
}
