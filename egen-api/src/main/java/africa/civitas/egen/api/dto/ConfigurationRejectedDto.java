package africa.civitas.egen.api.dto;

import java.util.List;

/**
 * Corps du {@code 400} d'une publication de configuration refusee : TOUTES les violations ensemble, chacune
 * nommant une cle et une regle, jamais une valeur (docs 13, A3).
 */
public class ConfigurationRejectedDto {
    public String error;
    public List<Violation> violations;

    public ConfigurationRejectedDto(String error, List<Violation> violations) {
        this.error = error;
        this.violations = violations;
    }

    public static class Violation {
        public String key;
        /** Code stable de la regle : UNKNOWN_KEY, SECRET_NOT_PUBLISHABLE, TYPE_MISMATCH, MISSING_REQUIRED_KEY, ou INVALID_VALUE (corps). */
        public String rule;
        public String message;

        public Violation(String key, String rule, String message) {
            this.key = key;
            this.rule = rule;
            this.message = message;
        }
    }
}
