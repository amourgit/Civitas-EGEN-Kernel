package africa.civitas.egen.bootstrap.wiring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 1 (docs/architecture/17) : le constat K6 de docs/architecture/24 — aucun mot de passe par défaut en
 * production — vérifié sans conteneur, sur la règle ET sur le fichier de configuration réel.
 */
class ProductionConfigurationRulesTest {

    @Test
    void anAbsentOrBlankPasswordRefusesToStartAndNamesTheVariable() {
        for (String missing : new String[] {null, "", "   "}) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> ProductionConfigurationRules.assertDatabasePasswordIsExplicit(missing));
            assertTrue(e.getMessage().contains("EGEN_REGISTRY_DB_PASSWORD"), e.getMessage());
        }
    }

    @Test
    void theHistoricalDevelopmentDefaultRefusesToStart() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ProductionConfigurationRules.assertDatabasePasswordIsExplicit("egen"));
        assertTrue(e.getMessage().contains("par défaut"), e.getMessage());
    }

    @Test
    void anyOtherExplicitPasswordIsAccepted() {
        assertDoesNotThrow(() -> ProductionConfigurationRules.assertDatabasePasswordIsExplicit("s3cret-propre-a-prod"));
        assertDoesNotThrow(() -> ProductionConfigurationRules.assertDatabasePasswordIsExplicit("Egen"),
                "seule la valeur exacte du développement est refusée");
    }

    // ------------------------------------------------------------------ le fichier de configuration réel

    private static Properties applicationProperties() throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(in, "application.properties doit être sur le classpath de test");
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        }
    }

    private static boolean isSecretName(String key) {
        String name = key.toLowerCase();
        return name.contains("password") || name.contains("secret");
    }

    @Test
    void everySecretPropertyHasAProductionOverrideWithNoDefaultValue() throws IOException {
        Properties properties = applicationProperties();
        List<String> violations = new ArrayList<>();

        for (String key : properties.stringPropertyNames()) {
            if (!isSecretName(key) || key.startsWith("%dev.") || key.startsWith("%test.")) {
                continue;
            }
            if (key.startsWith("%prod.")) {
                // « ${VARIABLE:} » : le défaut est vide ; « ${VARIABLE:valeur} » en aurait un.
                String value = properties.getProperty(key).trim();
                int colon = value.indexOf(':');
                boolean hasDefault = value.startsWith("${") && colon >= 0 && !value.substring(colon + 1, value.length() - 1).isEmpty();
                if (hasDefault || !value.startsWith("${")) {
                    violations.add(key + " = " + value + " — une valeur par défaut ou en clair en production");
                }
            } else if (properties.getProperty("%prod." + key) == null) {
                violations.add(key + " n'a pas de surcharge %prod. : sa valeur par défaut atteindrait la production");
            }
        }
        assertTrue(violations.isEmpty(), "Constat K6 :\n  " + String.join("\n  ", violations));
    }

    @Test
    void theDatabasePasswordInProductionComesOnlyFromTheEnvironmentVariable() throws IOException {
        assertEquals("${EGEN_REGISTRY_DB_PASSWORD:}", applicationProperties().getProperty("%prod.quarkus.datasource.password"));
    }
}
