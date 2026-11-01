package africa.civitas.egen.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrainte de version posee par une dependance de service (voir
 * docs/architecture/06-service-manifest.md, {@code dependencies.services[].versionConstraint}).
 *
 * <p>Grammaire : une ou plusieurs clauses separees par une virgule, toutes devant etre
 * satisfaites (ET). Une clause est un operateur suivi d'une version SemVer :
 * {@code >=}, {@code >}, {@code <=}, {@code <}, {@code =} ou {@code ==}. Une version seule vaut
 * egalite. Exemples : {@code ">=1.2.0"}, {@code ">=1.2.0,<2.0.0"}, {@code "3.0.1"}.</p>
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
            clauses.add(parseClause(part.trim(), raw));
        }
        return new VersionConstraint(clauses);
    }

    private static Clause parseClause(String text, String whole) {
        if (text.isEmpty()) {
            throw invalid(whole, "clause vide (virgule en trop ?)");
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
        try {
            return new Clause(operator, ServiceVersion.parse(versionText.trim()));
        } catch (ServiceVersion.InvalidServiceVersionException e) {
            throw invalid(whole, "« " + text + " » n'est pas « operateur version SemVer »");
        }
    }

    private static InvalidVersionConstraintException invalid(String whole, String reason) {
        return new InvalidVersionConstraintException(
                "Contrainte de version invalide \"" + whole + "\" : " + reason
                        + ". Format attendu : une ou plusieurs clauses separees par une virgule, chacune "
                        + "\">=\", \">\", \"<=\", \"<\" ou \"=\" suivi d'une version SemVer (ex. \">=1.2.0,<2.0.0\")");
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
