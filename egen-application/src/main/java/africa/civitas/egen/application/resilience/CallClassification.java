package africa.civitas.egen.application.resilience;

/**
 * Classification d'une reponse HTTP — identique pour tous les appelants
 * (docs/architecture/12, tableau "Classification d'une tentative") :
 * <ul>
 *   <li>{@code < 400} : succes ;</li>
 *   <li>{@code 5xx}, {@code 408}, {@code 429} : echec reessayable ;</li>
 *   <li>tout autre {@code 4xx} : reponse definitive (le service a repondu,
 *       la requete est invalide) — jamais reessayee, et le circuit la
 *       compte comme un succes.</li>
 * </ul>
 */
public final class CallClassification {

    private CallClassification() {
    }

    public static boolean isRetryableFailure(int httpStatus) {
        return httpStatus >= 500 || httpStatus == 408 || httpStatus == 429;
    }
}
