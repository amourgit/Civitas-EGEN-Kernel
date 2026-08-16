package africa.civitas.egen.kernel.pluginprocess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginProcessHandshakeTest {

    private static final String SHA256_VALIDE = "a".repeat(64);

    @Test
    void roundTripsThroughLigneAndDepuisLigne() {
        PluginProcessHandshake original = new PluginProcessHandshake(54321, SHA256_VALIDE);

        PluginProcessHandshake relu = PluginProcessHandshake.depuisLigne(original.ligne());

        assertEquals(original, relu);
    }

    @Test
    void ligneFollowsTheExactDocumentedFormat() {
        PluginProcessHandshake handshake = new PluginProcessHandshake(9999, SHA256_VALIDE);

        assertEquals("EGEN-PLUGIN-PROCESS-1|9999|" + SHA256_VALIDE, handshake.ligne());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65536, 100000})
    void rejectsAnOutOfRangePort(int portInvalide) {
        assertThrows(PluginProcessException.class,
                () -> new PluginProcessHandshake(portInvalide, SHA256_VALIDE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "pasunsha256", "A".repeat(64), "a".repeat(63), "a".repeat(65)})
    void rejectsAMalformedCertificateFingerprint(String empreinteInvalide) {
        assertThrows(PluginProcessException.class,
                () -> new PluginProcessHandshake(1234, empreinteInvalide));
    }

    @Test
    void rejectsANullCertificateFingerprint() {
        assertThrows(NullPointerException.class, () -> new PluginProcessHandshake(1234, null));
    }

    @Test
    void rejectsANullLine() {
        assertThrows(PluginProcessException.class, () -> PluginProcessHandshake.depuisLigne(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AUTRE-CHOSE|123|" + "a".repeat(64),
            "EGEN-PLUGIN-PROCESS-2|123|" + "a".repeat(64),
            "EGEN-PLUGIN-PROCESS-1|123",
            "EGEN-PLUGIN-PROCESS-1|123|" + "a".repeat(64) + "|trop",
            "n'importe quoi"
    })
    void rejectsALineWithTheWrongShapeOrMagicVersion(String ligneInvalide) {
        assertThrows(PluginProcessException.class, () -> PluginProcessHandshake.depuisLigne(ligneInvalide));
    }

    @Test
    void rejectsALineWithANonNumericPort() {
        assertThrows(PluginProcessException.class,
                () -> PluginProcessHandshake.depuisLigne("EGEN-PLUGIN-PROCESS-1|pasunport|" + SHA256_VALIDE));
    }
}
