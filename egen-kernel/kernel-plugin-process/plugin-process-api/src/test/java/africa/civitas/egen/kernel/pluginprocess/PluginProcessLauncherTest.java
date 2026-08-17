package africa.civitas.egen.kernel.pluginprocess;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lance de vrais processus {@code sh} — pas de simulation. Le plugin lui-meme n'a
 * pas besoin d'etre du Java : {@link PluginProcessLauncher} ne regarde jamais que sa
 * sortie standard, exactement comme il le ferait face a un vrai processus plugin.
 */
class PluginProcessLauncherTest {

    private static final Path REPERTOIRE_TRAVAIL = Path.of(System.getProperty("java.io.tmpdir"));

    /** Le meme vrai certificat X.509 (EC secp256r1) que PluginProcessHandshakeTest, genere via keytool. */
    private static final String CERTIFICAT_VALIDE_BASE64 =
            "MIIBNTCB3KADAgECAgkAhLh07YXy52AwCgYIKoZIzj0EAwIwDzENMAsGA1UEAxMEdGVzdDAeFw0yNjA4MTcwMDU4NDRaFw0yNjA4MTgwMDU4NDRaMA8xDTALBgNVBAMTBHRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATpxvxsrnmcuF+MMMvuEWdE7MWuum3KER3vCWvCOS++Vzqt2gzULnZt5y1+qd6HHwePHeAxLRH3E+9VNiRi6pH4oyEwHzAdBgNVHQ4EFgQUxP1r52FCQ/PXN4yWOw0xuud7hhUwCgYIKoZIzj0EAwIDSAAwRQIhALKME1co4El9evIIISbKdiXd/hJfum0y0mMMhnJWEJ+rAiBDBZ+SL9rujSjys6a3yjSkMiYBttSJMii5CE9tGIX4aQ==";

    @Test
    void readsAValidHandshakeFromARealSubprocess() {
        PluginProcessLauncher launcher = new PluginProcessLauncher(Duration.ofSeconds(5));
        String ligneAttendue = "EGEN-PLUGIN-PROCESS-1|9999|" + CERTIFICAT_VALIDE_BASE64;

        PluginProcessHandle handle = launcher.lancer(
                List.of("sh", "-c", "echo '" + ligneAttendue + "'; sleep 5"), REPERTOIRE_TRAVAIL, Map.of());
        try {
            assertEquals(9999, handle.handshake().port());
            assertEquals(CERTIFICAT_VALIDE_BASE64, handle.handshake().certificatServeurBase64());
            assertTrue(handle.estVivant());
        } finally {
            handle.arreter(Duration.ofSeconds(2));
        }
    }

    @Test
    void stoppingTheHandleTerminatesTheRealProcess() {
        PluginProcessLauncher launcher = new PluginProcessLauncher(Duration.ofSeconds(5));
        PluginProcessHandle handle = launcher.lancer(
                List.of("sh", "-c", "echo 'EGEN-PLUGIN-PROCESS-1|1234|" + CERTIFICAT_VALIDE_BASE64 + "'; sleep 30"),
                REPERTOIRE_TRAVAIL, Map.of());

        handle.arreter(Duration.ofSeconds(2));

        assertFalse(handle.estVivant());
    }

    @Test
    void timesOutWhenTheProcessNeverSendsAHandshake() {
        PluginProcessLauncher launcherAvecDelaiCourt = new PluginProcessLauncher(Duration.ofMillis(500));

        long debut = System.currentTimeMillis();
        PluginProcessException exception = assertThrows(PluginProcessException.class,
                () -> launcherAvecDelaiCourt.lancer(List.of("sh", "-c", "sleep 5"), REPERTOIRE_TRAVAIL, Map.of()));
        long duree = System.currentTimeMillis() - debut;

        assertTrue(duree < 4000, "le timeout doit se declencher bien avant la fin du sleep 5s reel, mis " + duree + "ms");
        assertTrue(exception.getMessage().contains("delai imparti"));
    }

    @Test
    void detectsAProcessThatExitsBeforeSendingAHandshake() {
        PluginProcessLauncher launcher = new PluginProcessLauncher(Duration.ofSeconds(5));

        PluginProcessException exception = assertThrows(PluginProcessException.class,
                () -> launcher.lancer(List.of("sh", "-c", "exit 3"), REPERTOIRE_TRAVAIL, Map.of()));

        assertTrue(exception.getMessage().contains("termine avant"));
    }

    @Test
    void rejectsAMalformedHandshakeLineAndKillsTheProcess() {
        PluginProcessLauncher launcher = new PluginProcessLauncher(Duration.ofSeconds(5));

        assertThrows(PluginProcessException.class,
                () -> launcher.lancer(List.of("sh", "-c", "echo 'PAS-UN-HANDSHAKE-VALIDE'; sleep 5"),
                        REPERTOIRE_TRAVAIL, Map.of()));
    }

    @Test
    void rejectsAnEmptyCommand() {
        PluginProcessLauncher launcher = new PluginProcessLauncher(Duration.ofSeconds(5));

        assertThrows(IllegalArgumentException.class, () -> launcher.lancer(List.of(), REPERTOIRE_TRAVAIL, Map.of()));
    }

    @Test
    void rejectsANonPositiveHandshakeDelay() {
        assertThrows(IllegalArgumentException.class, () -> new PluginProcessLauncher(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new PluginProcessLauncher(Duration.ofSeconds(-1)));
    }
}
