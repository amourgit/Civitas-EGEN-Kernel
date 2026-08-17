package africa.civitas.egen.kernel.pluginprocess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginProcessHandshakeTest {

    /**
     * Un vrai certificat X.509 auto-signe (EC secp256r1), genere une fois pour ce
     * fichier de test via {@code keytool} — jamais une chaine inventee, puisque
     * {@link PluginProcessHandshake} valide desormais que le contenu decode bien
     * vers un certificat X.509 reel, pas seulement vers du Base64 syntaxiquement
     * correct.
     */
    private static final String CERTIFICAT_VALIDE_BASE64 =
            "MIIBNTCB3KADAgECAgkAhLh07YXy52AwCgYIKoZIzj0EAwIwDzENMAsGA1UEAxMEdGVzdDAeFw0yNjA4MTcwMDU4NDRaFw0yNjA4MTgwMDU4NDRaMA8xDTALBgNVBAMTBHRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATpxvxsrnmcuF+MMMvuEWdE7MWuum3KER3vCWvCOS++Vzqt2gzULnZt5y1+qd6HHwePHeAxLRH3E+9VNiRi6pH4oyEwHzAdBgNVHQ4EFgQUxP1r52FCQ/PXN4yWOw0xuud7hhUwCgYIKoZIzj0EAwIDSAAwRQIhALKME1co4El9evIIISbKdiXd/hJfum0y0mMMhnJWEJ+rAiBDBZ+SL9rujSjys6a3yjSkMiYBttSJMii5CE9tGIX4aQ==";

    @Test
    void roundTripsThroughLigneAndDepuisLigne() {
        PluginProcessHandshake original = new PluginProcessHandshake(54321, CERTIFICAT_VALIDE_BASE64);

        PluginProcessHandshake relu = PluginProcessHandshake.depuisLigne(original.ligne());

        assertEquals(original, relu);
    }

    @Test
    void ligneFollowsTheExactDocumentedFormat() {
        PluginProcessHandshake handshake = new PluginProcessHandshake(9999, CERTIFICAT_VALIDE_BASE64);

        assertEquals("EGEN-PLUGIN-PROCESS-1|9999|" + CERTIFICAT_VALIDE_BASE64, handshake.ligne());
    }

    @Test
    void certificatServeurDerReturnsTheRawDecodedBytes() {
        PluginProcessHandshake handshake = new PluginProcessHandshake(1234, CERTIFICAT_VALIDE_BASE64);

        byte[] attendu = java.util.Base64.getDecoder().decode(CERTIFICAT_VALIDE_BASE64);

        assertTrue(java.util.Arrays.equals(attendu, handshake.certificatServeurDer()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65536, 100000})
    void rejectsAnOutOfRangePort(int portInvalide) {
        assertThrows(PluginProcessException.class,
                () -> new PluginProcessHandshake(portInvalide, CERTIFICAT_VALIDE_BASE64));
    }

    @Test
    void rejectsSyntacticallyInvalidBase64() {
        assertThrows(PluginProcessException.class,
                () -> new PluginProcessHandshake(1234, "ceci n'est pas du Base64 valide !!!"));
    }

    @Test
    void rejectsValidBase64ThatIsNotAnX509Certificate() {
        String base64ValideMaisPasUnCertificat =
                java.util.Base64.getEncoder().encodeToString("bonjour, ceci n'est pas un certificat".getBytes());

        assertThrows(PluginProcessException.class,
                () -> new PluginProcessHandshake(1234, base64ValideMaisPasUnCertificat));
    }

    @Test
    void rejectsANullCertificate() {
        assertThrows(NullPointerException.class, () -> new PluginProcessHandshake(1234, null));
    }

    @Test
    void rejectsANullLine() {
        assertThrows(PluginProcessException.class, () -> PluginProcessHandshake.depuisLigne(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AUTRE-CHOSE|123|",
            "EGEN-PLUGIN-PROCESS-2|123|",
            "EGEN-PLUGIN-PROCESS-1|123",
            "n'importe quoi"
    })
    void rejectsALineWithTheWrongShapeOrMagicVersion(String prefixeInvalide) {
        String ligne = prefixeInvalide.endsWith("|") ? prefixeInvalide + CERTIFICAT_VALIDE_BASE64 : prefixeInvalide;

        assertThrows(PluginProcessException.class, () -> PluginProcessHandshake.depuisLigne(ligne));
    }

    @Test
    void rejectsALineWithANonNumericPort() {
        assertThrows(PluginProcessException.class,
                () -> PluginProcessHandshake.depuisLigne(
                        "EGEN-PLUGIN-PROCESS-1|pasunport|" + CERTIFICAT_VALIDE_BASE64));
    }

    @Test
    void rejectsALineWithTooManySegments() {
        assertThrows(PluginProcessException.class,
                () -> PluginProcessHandshake.depuisLigne(
                        "EGEN-PLUGIN-PROCESS-1|123|" + CERTIFICAT_VALIDE_BASE64 + "|trop"));
    }
}
