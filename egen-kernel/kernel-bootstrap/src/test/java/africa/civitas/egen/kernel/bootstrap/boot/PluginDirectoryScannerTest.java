package africa.civitas.egen.kernel.bootstrap.boot;

import africa.civitas.egen.kernel.pluginengine.loader.CandidatModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDirectoryScannerTest {

    private final PluginDirectoryScanner scanner = new PluginDirectoryScanner();

    @TempDir
    Path repertoire;

    private void creerFichier(String nom) throws IOException {
        Files.writeString(repertoire.resolve(nom), "contenu de test");
    }

    @Test
    void returnsAnEmptyListForAMissingDirectory() {
        List<CandidatModule> candidats = scanner.scanner(repertoire.resolve("absent"));

        assertTrue(candidats.isEmpty());
    }

    @Test
    void returnsAnEmptyListForAnEmptyDirectory() {
        List<CandidatModule> candidats = scanner.scanner(repertoire);

        assertTrue(candidats.isEmpty());
    }

    @Test
    void findsAJarWithAMatchingPropertiesFile() throws IOException {
        creerFichier("academie.jar");
        creerFichier("academie.properties");

        List<CandidatModule> candidats = scanner.scanner(repertoire);

        assertEquals(1, candidats.size());
        assertEquals(repertoire.resolve("academie.jar"), candidats.get(0).cheminPlugin());
    }

    @Test
    void ignoresAJarWithoutAMatchingPropertiesFile() throws IOException {
        creerFichier("academie.jar");

        List<CandidatModule> candidats = scanner.scanner(repertoire);

        assertTrue(candidats.isEmpty());
    }

    @Test
    void ignoresAPropertiesFileWithoutAMatchingJar() throws IOException {
        creerFichier("academie.properties");

        List<CandidatModule> candidats = scanner.scanner(repertoire);

        assertTrue(candidats.isEmpty());
    }

    @Test
    void ignoresNonJarFiles() throws IOException {
        creerFichier("lisez-moi.txt");
        creerFichier("academie.jar");
        creerFichier("academie.properties");

        List<CandidatModule> candidats = scanner.scanner(repertoire);

        assertEquals(1, candidats.size());
    }

    @Test
    void findsMultipleCandidatesInAReproducibleOrder() throws IOException {
        creerFichier("rh.jar");
        creerFichier("rh.properties");
        creerFichier("academie.jar");
        creerFichier("academie.properties");

        List<CandidatModule> candidats = scanner.scanner(repertoire);

        assertEquals(2, candidats.size());
        assertEquals(repertoire.resolve("academie.jar"), candidats.get(0).cheminPlugin());
        assertEquals(repertoire.resolve("rh.jar"), candidats.get(1).cheminPlugin());
    }

    @Test
    void rejectsANullDirectory() {
        assertThrows(IllegalArgumentException.class, () -> scanner.scanner(null));
    }
}
