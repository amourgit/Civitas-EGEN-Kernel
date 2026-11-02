package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.ConfigurationSpec;

/**
 * Une raison de refuser une publication de configuration : la cle fautive et la regle enfreinte, jamais la valeur
 * (voir docs/architecture/13-api-et-contrats.md, « Publication de configuration »).
 */
public record ConfigurationViolation(String key, ConfigurationSpec.Rule rule, String message) {

    static ConfigurationViolation from(ConfigurationSpec.Violation violation) {
        return new ConfigurationViolation(violation.key(), violation.rule(), violation.message());
    }
}
