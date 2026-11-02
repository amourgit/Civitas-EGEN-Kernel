package africa.civitas.egen.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrainte de version posee par une dependance de service (voir
 * docs/architecture/06-service-manifest.md, {@code dependencies.services[].versionConstraint}).
 *
 * <p>Grammaire : une ou plusieurs clauses separees par une virgule, toutes devant etre
 * satisfaites (ET). Une clause est :</p>
 * <ul>
 *   <li>un operateur {@code >=}, {@code >}, {@code <=}, {@code <}, {@code =} ou {@code ==} suivi d'une
 *       version SemVer complete ; une version seule vaut egalite ;</li>
 *   <li>{@code ^X.Y.Z} (« compatible », comme npm et Cargo) : {@code >=X.Y.Z} et strictement inferieur a la
 *       prochaine version incompatible — {@code ^1.2.3} = {@code >=1.2.3,<2.0.0}, {@code ^0.2.3} =
 *       {@code >=0.2.3,<0.3.0}, {@code ^0.0.3} = {@code >=0.0.3,<0.0.4} ;</li>
 *   <li>{@code ~X.Y.Z} (« meme mineure ») : {@code ~1.2.3} = {@code >=1.2.3,<1.3.0}.</li>
 * </ul>
 * <p>Avec {@code >=}, {@code ^} et {@code ~}, les composantes manquantes valent 0 ({@code ^2.0},
 * {@code >=1.2}, {@code ~2}). Partout ailleurs une version complete est exigee : {@code >1.2} serait
 * ambigu. Exemples : {@code ">=1.2.0"}, {@code ">=1.2.0,<2.0.0"}, {@code "^2.0"}, {@code "3.0.1"}.</p>
 *
 * <p>Comme tout Value Object du domaine, une contrainte illisible refuse de se construire : le
 * manifeste qui la porte est invalide et refuse a {@code Declare} (voir 05, « Regle de
 * conception »). La comparaison des versions est celle de {@link ServiceVersion}, dont la
 * precedence SemVer place une pre-release avant sa version finale.</p>
 */
public record VersionConstraint(List<Clause> clauses) {

    /** Operateurs de comparaison reconnus. */
    public enum Operator {
        GREATER_OR_EQUAL(">="),
        LESS_OR_EQUAL("<="),
        GREATER(">"),
        LESS("<"),
        EQUAL("=");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        boolean test(int comparison) {
            return switch (this) {
                case GREATER_OR_EQUAL -> comparison >= 0;
                case LESS_OR_EQUAL -> comparison <= 0;
                case GREATER -> comparison > 0;
                case LESS -> comparison < 0;
                case EQUAL -> comparison == 0;
            };
        }
    }

    /** Une comparaison unitaire {@code operateur version}. */
    public record Clause(Operator operator, ServiceVersion version) {
        public Clause {
            if (operator == null || version == null) {
                throw new InvalidVersionConstraintException("Une clause de contrainte exige un operateur et une version");
            }
        }

        boolean isSatisfiedBy(ServiceVersion candidate) {
            return operator.test(candidate.compareTo(version));
        }

        @Override
        public String toString() {
            return operator.symbol() + version;
        }
    }

    public VersionConstraint {
        if (clauses == null || clauses.isEmpty()) {
            throw new InvalidVersionConstraintException("Une contrainte de version exige au moins une clause");
        }
        clauses = List.copyOf(clauses);
    }

    /**
     * Analyse une contrainte.
     *
     * @throws InvalidVersionConstraintException si la chaine est vide ou ne respecte pas la grammaire
     */
    public static VersionConstraint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidVersionConstraintException("La contrainte de version ne peut pas etre vide");
        }
        List<Clause> clauses = new ArrayList<>();
        for (String part : raw.split(",", -1)) {
            clauses.addAll(parseClause(part.trim(), raw));
        }
        return new VersionConstraint(clauses);
    }

    private static List<Clause> parseClause(String text, String whole) {
        if (text.isEmpty()) {
            throw invalid(whole, "clause vide (virgule en trop ?)");
        }
        // Operateurs « intervalle » : une clause s'etend en deux bornes.
        if (text.startsWith("^") || text.startsWith("~")) {
            return parseRange(text.charAt(0), text.substring(1).trim(), text, whole);
        }
        Operator operator = Operator.EQUAL;
        String versionText = text;
        // L'ordre compte : les operateurs a deux caracteres avant ceux a un caractere.
        if (text.startsWith(">=")) {
            operator = Operator.GREATER_OR_EQUAL;
            versionText = text.substring(2);
        } else if (text.startsWith("<=")) {
            operator = Operator.LESS_OR_EQUAL;
            versionText = text.substring(2);
        } else if (text.startsWith("==")) {
            versionText = text.substring(2);
        } else if (text.startsWith(">")) {
            operator = Operator.GREATER;
            versionText = text.substring(1);
        } else if (text.startsWith("<")) {
            operator = Operator.LESS;
            versionText = text.substring(1);
        } else if (text.startsWith("=")) {
            versionText = text.substring(1);
        }
        versionText = versionText.trim();
        // « >=1.2 » vaut « >=1.2.0 » ; les autres operateurs exigent une version complete.
        if (operator == Operator.GREATER_OR_EQUAL) {
            versionText = completeMissingComponents(versionText);
        }
        try {
            return List.of(new Clause(operator, ServiceVersion.parse(versionText)));
        } catch (ServiceVersion.InvalidServiceVersionException e) {
            throw invalid(whole, "« " + text + " » n'est pas « operateur version SemVer »");
        }
    }

    private static List<Clause> parseRange(char symbol, String versionText, String text, String whole) {
        ServiceVersion base;
        try {
            base = ServiceVersion.parse(completeMissingComponents(versionText));
        } catch (ServiceVersion.InvalidServiceVersionException e) {
            throw invalid(whole, "« " + text + " » n'est pas « " + symbol + " version SemVer »");
        }
        ServiceVersion upper;
        if (symbol == '~') {
            upper = new ServiceVersion(base.major(), base.minor() + 1, 0, null);
        } else if (base.major() > 0) {
            upper = new ServiceVersion(base.major() + 1, 0, 0, null);
        } else if (base.minor() > 0) {
            upper = new ServiceVersion(0, base.minor() + 1, 0, null);
        } else {
            upper = new ServiceVersion(0, 0, base.patch() + 1, null);
        }
        return List.of(new Clause(Operator.GREATER_OR_EQUAL, base), new Clause(Operator.LESS, upper));
    }

    /** {@code "2"} -> {@code "2.0.0"}, {@code "2.1"} -> {@code "2.1.0"} ; tout autre texte est rendu tel quel. */
    private static String completeMissingComponents(String versionText) {
        if (versionText.matches("\\d+")) {
            return versionText + ".0.0";
        }
        if (versionText.matches("\\d+\\.\\d+")) {
            return versionText + ".0";
        }
        return versionText;
    }

    private static InvalidVersionConstraintException invalid(String whole, String reason) {
        return new InvalidVersionConstraintException(
                "Contrainte de version invalide \"" + whole + "\" : " + reason
                        + ". Format attendu : une ou plusieurs clauses separees par une virgule, chacune "
                        + "\">=\", \">\", \"<=\", \"<\", \"=\", \"^\" ou \"~\" suivi d'une version SemVer "
                        + "(ex. \">=1.2.0,<2.0.0\" ou \"^2.0\")");
    }

    /** Vrai si {@code candidate} satisfait toutes les clauses. */
    public boolean isSatisfiedBy(ServiceVersion candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("La version a verifier est obligatoire");
        }
        return clauses.stream().allMatch(clause -> clause.isSatisfiedBy(candidate));
    }

    /** Forme canonique, relisible par {@link #parse(String)} : ex. {@code ">=1.2.0,<2.0.0"}. */
    public String expression() {
        return String.join(",", clauses.stream().map(Clause::toString).toList());
    }

    @Override
    public String toString() {
        return expression();
    }

    /** Signale une contrainte de version illisible. */
    public static final class InvalidVersionConstraintException extends IllegalArgumentException {
        public InvalidVersionConstraintException(String message) {
            super(message);
        }
    }
}
