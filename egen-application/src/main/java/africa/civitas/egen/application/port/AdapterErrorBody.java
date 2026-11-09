package africa.civitas.egen.application.port;

/**
 * Corps d'une reponse d'erreur d'un systeme externe (Nomad, Kubernetes, Consul, Vault), borne avant d'etre recopie
 * dans le message d'une exception d'adapter (constat K8, docs/architecture/24 ; contrat dans
 * docs/architecture/07-ports-et-adapters.md, « Messages d'erreur des adapters HTTP »).
 *
 * <p>Le corps est conserve : il dit souvent exactement ce qui est refuse (un champ invalide, un droit manquant) et
 * c'est ce qui permet de deboguer. Il est seulement borne a {@link #MAX_LENGTH} caracteres Java, car un corps non
 * borne (une page HTML renvoyee par un mandataire, par exemple) entrerait en entier dans le journal et dans la
 * Condition du service.
 */
public final class AdapterErrorBody {

    /** Nombre maximal de caracteres Java (unites UTF-16, comme {@link String#length()}) conserves du corps. */
    public static final int MAX_LENGTH = 500;

    /** Suffixe ajoute apres les {@link #MAX_LENGTH} premiers caracteres quand le corps a ete coupe. */
    public static final String TRUNCATION_MARK = " [tronque]";

    private AdapterErrorBody() {
    }

    /**
     * @param body corps de la reponse, ou {@code null}
     * @return le corps tel quel s'il fait au plus {@link #MAX_LENGTH} caracteres ; sinon ses {@link #MAX_LENGTH}
     *         premiers caracteres suivis de {@link #TRUNCATION_MARK} ; une chaine vide si le corps est {@code null}.
     *         Un caractere hors du plan de base (deux unites UTF-16) n'est jamais coupe en deux.
     */
    public static String bounded(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= MAX_LENGTH) {
            return body;
        }
        int end = MAX_LENGTH;
        if (Character.isHighSurrogate(body.charAt(end - 1))) {
            end--;
        }
        return body.substring(0, end) + TRUNCATION_MARK;
    }
}
