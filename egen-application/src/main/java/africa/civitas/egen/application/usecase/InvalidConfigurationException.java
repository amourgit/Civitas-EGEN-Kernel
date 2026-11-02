package africa.civitas.egen.application.usecase;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Une publication de configuration est refusee. Porte TOUTES les violations (l'API les renvoie ensemble) ;
 * le message ne cite que des cles et des regles, jamais une valeur.
 */
public final class InvalidConfigurationException extends RuntimeException {

    private final List<ConfigurationViolation> violations;

    public InvalidConfigurationException(List<ConfigurationViolation> violations) {
        super("Configuration refusee : " + violations.stream()
                .map(v -> v.key() + " " + v.message())
                .collect(Collectors.joining(" ; ")));
        this.violations = List.copyOf(violations);
    }

    public List<ConfigurationViolation> violations() {
        return violations;
    }
}
