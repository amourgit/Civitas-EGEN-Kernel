package africa.civitas.egen.domain.model;

import africa.civitas.egen.domain.model.ConfigurationSpec.Rule;
import africa.civitas.egen.domain.model.ConfigurationSpec.Violation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Niveau 1 (docs 17). Contrat : 13, « Publication de configuration », regles de validation 2 a 5. */
class ConfigurationSpecPublicationTest {

    private final ConfigurationSpec spec = new ConfigurationSpec(
            List.of(new ConfigurationSpec.Key("db.host", "string", null),
                    new ConfigurationSpec.Key("db.port", "integer", null),
                    new ConfigurationSpec.Key("cache.enabled", "boolean", "true"),
                    new ConfigurationSpec.Key("ratio", "number", "0.5")),
            List.of(new ConfigurationSpec.SecretReferenceSpec("db.password", "vault", "secret/news/db")));

    @Test
    void aCompletePublicationIsAccepted() {
        assertTrue(spec.validatePublication(Map.of("db.host", "pg.internal", "db.port", "5432")).isEmpty());
    }

    @Test
    void defaultsMakeAKeyOptionalButTheValueIsStillCheckedWhenGiven() {
        assertTrue(spec.validatePublication(
                Map.of("db.host", "h", "db.port", "1", "cache.enabled", "false", "ratio", "1.5")).isEmpty());

        List<Violation> violations = spec.validatePublication(
                Map.of("db.host", "h", "db.port", "1", "cache.enabled", "peut-etre"));

        assertEquals(List.of(Rule.TYPE_MISMATCH), violations.stream().map(Violation::rule).toList());
        assertEquals("cache.enabled", violations.get(0).key());
    }

    @Test
    void aKeyTheManifestDoesNotDeclareIsRefused() {
        List<Violation> violations = spec.validatePublication(
                Map.of("db.host", "h", "db.port", "1", "surprise", "x"));

        assertEquals(List.of(new Violation("surprise", Rule.UNKNOWN_KEY, violations.get(0).message())), violations);
    }

    @Test
    void aSecretIsNeverPublishedAsAValue() {
        List<Violation> violations = spec.validatePublication(
                Map.of("db.host", "h", "db.port", "1", "db.password", "hunter2"));

        assertEquals(1, violations.size());
        assertEquals(Rule.SECRET_NOT_PUBLISHABLE, violations.get(0).rule());
        assertEquals("db.password", violations.get(0).key());
    }

    @Test
    void everyKeyWithoutDefaultMustBeProvidedAndAllMissingKeysAreListed() {
        List<Violation> violations = spec.validatePublication(Map.of());

        assertEquals(List.of("db.host", "db.port"), violations.stream().map(Violation::key).toList());
        assertTrue(violations.stream().allMatch(v -> v.rule() == Rule.MISSING_REQUIRED_KEY));
    }

    @Test
    void allViolationsAreReturnedTogetherSortedByKey() {
        List<Violation> violations = spec.validatePublication(
                Map.of("db.port", "pas-un-nombre", "zzz", "x", "db.password", "p"));

        assertEquals(List.of(
                        "db.host:MISSING_REQUIRED_KEY",
                        "db.password:SECRET_NOT_PUBLISHABLE",
                        "db.port:TYPE_MISMATCH",
                        "zzz:UNKNOWN_KEY"),
                violations.stream().map(v -> v.key() + ":" + v.rule()).toList());
    }

    @Test
    void aViolationNeverCarriesTheRefusedValue() {
        List<Violation> violations = spec.validatePublication(
                Map.of("db.host", "h", "db.port", "VALEUR-SECRETE-123", "db.password", "VALEUR-SECRETE-456"));

        for (Violation violation : violations) {
            assertFalse(violation.message().contains("VALEUR-SECRETE"), violation.message());
            assertFalse(violation.key().contains("VALEUR-SECRETE"));
        }
    }

    @Test
    void aServiceWithoutConfigurationAcceptsOnlyAnEmptyPublication() {
        ConfigurationSpec none = ConfigurationSpec.none();

        assertTrue(none.validatePublication(Map.of()).isEmpty());
        assertEquals(Rule.UNKNOWN_KEY, none.validatePublication(Map.of("x", "y")).get(0).rule());
    }
}
