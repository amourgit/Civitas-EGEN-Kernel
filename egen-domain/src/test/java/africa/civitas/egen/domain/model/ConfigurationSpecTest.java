package africa.civitas.egen.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationSpecTest {

    @Test
    void theBlockOfDoc06IsRepresentable() {
        ConfigurationSpec spec = new ConfigurationSpec(
                List.of(new ConfigurationSpec.Key("FEATURE_FLAG_COMMENTS", "boolean", "false")),
                List.of(new ConfigurationSpec.SecretReferenceSpec("DB_PASSWORD", "vault", "secret/data/news-service/db")));

        assertEquals("boolean", spec.required().get(0).type());
        assertEquals("false", spec.required().get(0).defaultValue());
        assertEquals("secret/data/news-service/db", spec.secretsRefs().get(0).path());
        assertFalse(spec.isEmpty());
    }

    @Test
    void noConfigurationIsTheDefault() {
        assertTrue(ConfigurationSpec.none().isEmpty());
        assertTrue(new ConfigurationSpec(null, null).isEmpty());
    }

    @Test
    void theTypeDefaultsToStringAndIsCaseInsensitive() {
        assertEquals("string", new ConfigurationSpec.Key("A", null, null).type());
        assertEquals("string", new ConfigurationSpec.Key("A", " ", null).type());
        assertEquals("boolean", new ConfigurationSpec.Key("A", "BOOLEAN", "true").type());
        assertNull(new ConfigurationSpec.Key("A", "string", null).defaultValue());
    }

    @Test
    void anUnknownTypeIsRejectedWithAMessageNamingTheKey() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new ConfigurationSpec.Key("RETRIES", "float", null)).getMessage().contains("RETRIES"));
    }

    @Test
    void aDefaultMustConformToItsType() {
        new ConfigurationSpec.Key("A", "boolean", "true");
        new ConfigurationSpec.Key("A", "integer", "-42");
        new ConfigurationSpec.Key("A", "number", "3.14");
        new ConfigurationSpec.Key("A", "string", "n'importe quoi");
        for (String[] bad : new String[][] {{"boolean", "yes"}, {"boolean", "TRUE"}, {"integer", "1.5"},
                {"integer", "abc"}, {"number", "x"}, {"number", ""}}) {
            assertThrows(IllegalArgumentException.class, () -> new ConfigurationSpec.Key("A", bad[0], bad[1]),
                    bad[0] + " " + bad[1]);
        }
    }

    @Test
    void acceptsChecksAResolvedValueAgainstTheType() {
        ConfigurationSpec.Key flag = new ConfigurationSpec.Key("A", "boolean", null);
        assertTrue(flag.accepts("true"));
        assertFalse(flag.accepts("maybe"));
        assertFalse(flag.accepts(null));
        assertTrue(new ConfigurationSpec.Key("B", "string", null).accepts(""));
    }

    @Test
    void namesAreEnvironmentSafe() {
        for (String bad : new String[] {null, "", "1A", "A B", "A=B", "A\nB", "é"}) {
            assertThrows(IllegalArgumentException.class, () -> new ConfigurationSpec.Key(bad, null, null), String.valueOf(bad));
            assertThrows(IllegalArgumentException.class,
                    () -> new ConfigurationSpec.SecretReferenceSpec(bad, "vault", "p"), String.valueOf(bad));
        }
    }

    @Test
    void secretReferencesNeedAProviderAndAPath() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigurationSpec.SecretReferenceSpec("K", " ", "p"));
        assertThrows(IllegalArgumentException.class, () -> new ConfigurationSpec.SecretReferenceSpec("K", "vault", null));
    }

    @Test
    void aKeyIsDeclaredOnlyOnce_evenAcrossConfigurationAndSecrets() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigurationSpec(
                List.of(new ConfigurationSpec.Key("A", null, null), new ConfigurationSpec.Key("A", null, null)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ConfigurationSpec(
                List.of(new ConfigurationSpec.Key("A", null, null)),
                List.of(new ConfigurationSpec.SecretReferenceSpec("A", "vault", "p"))));
    }
}
