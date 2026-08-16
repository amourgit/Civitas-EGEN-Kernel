package africa.civitas.egen.kernel.pluginprocess;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

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

    @Test
    void readsAValidHandshakeFromARealSubprocess() {
        PluginProcessLauncher launcher = new PluginProcessLauncher(Duration.ofSeconds(5));
        String ligneAttendue = "EGEN-PLUGIN-PROCESS-1|9999|" + "b".repeat(64);

        PluginProcessHandle handle = launcher.lancer(
                List.of("sh", "-c", "echo '" + ligneAttendue + "'; sleep 5"), REPERTOIRE_TRAVAIL);
        try {
            assertEquals(9999, handle.handshake().port());
            assertEquals("b".repeat(64), handle.handshake().certificatServeurSha256());
            assertTrue(handle.estVivant());
        } finally {
            handle.arreter(Duration.ofSeconds(2));
        }
    }

    @Test
    void stoppingTheHandleTerminatesTheRealProcess() {
        PluginProcessLauncher launcher = new PluginProcessLauncher(Duration.ofSeconds(5));
        PluginProcessHandle handle = launcher.lancer(
                List.of("sh", "-c", "echo 'EGEN-PLUGIN-PROCESS-1|1234|" + "c".repeat(64) + "'; sleep 30"),
                REPERTOIRE_TRAVAIL);

        handle.arreter(Duration.ofSeconds(2));

        assertFalse(handle.estVivant());
    }

    @Test
    void timesOutWhenTheProcessNeverSendsAHandshake() {
        PluginProcessLauncher launcherAvecDelaiCourt = new PluginProcessLauncher(Duration.ofMillis(500));

        long debut = System.currentTimeMillis();
        PluginProcessException exception = assertThrows(PluginProcessException.class,
                () -> launcherAvecDelaiCourt.lancer(List.of("sh", "-c", "sleep 5"), REPERTOIRE_TRAVAIL));
        long duree = System.currentTimeMillis() - debut;

        assertTrue(duree < 4000, "le timeout doit se declencher bien avant la fin du sleep 5s reel, mis " + duree + "ms");
        assertTrue(exception.getMessage().contains("delai imparti"));
    }

    @Test
    void detectsAProcessThatExitsBeforeSendingAHandshake() {
        PluginProcessLauncher launcher = new PluginProcessLauncher(Duration.ofSeconds(5));

        PluginProcessException exception = assertThrows(PluginProcessException.class,
                () -> launcher.lancer(List.of("sh", "-c", "exit 3"), REPERTOIRE_TRAVAIL));

        assertTrue(exception.getMessage().contains("termine avant"));
    }

    @Test
    void rejectsAMalformedHandshakeLineAndKillsTheProcess() {
        PluginProcessLauncher launcher = new PluginProcessLauncher(Duration.ofSeconds(5));

        assertThrows(PluginProcessException.class,
                () -> launcher.lancer(List.of("sh", "-c", "echo 'PAS-UN-HANDSHAKE-VALIDE'; sleep 5"),
                        REPERTOIRE_TRAVAIL));
    }

    @Test
    void rejectsAnEmptyCommand() {
        PluginProcessLauncher launcher = new PluginProcessLauncher(Duration.ofSeconds(5));

        assertThrows(IllegalArgumentException.class, () -> launcher.lancer(List.of(), REPERTOIRE_TRAVAIL));
    }

    @Test
    void rejectsANonPositiveHandshakeDelay() {
        assertThrows(IllegalArgumentException.class, () -> new PluginProcessLauncher(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new PluginProcessLauncher(Duration.ofSeconds(-1)));
    }
}
