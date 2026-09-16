package africa.civitas.egen.kernel.bootstrap.boot;

import africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReader;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionRegistry;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie {@link KernelBootSequence} de bout en bout, entierement sans Quarkus ni
 * Docker : charger un module au demarrage est une operation administrative simple
 * (voir {@link PluginLifecycleManager}), qui ne depend d'aucune infrastructure
 * externe pour etre testee.
 */
class KernelBootSequenceTest {

    private void creerCandidat(Path repertoire, String moduleId, String version) throws IOException {
        Files.writeString(repertoire.resolve(moduleId + ".jar"), "contenu factice, jamais lu par FakePluginLoader");
        Files.writeString(repertoire.resolve(moduleId + ".properties"),
                "moduleId=" + moduleId + "\nversion=" + version + "\n");
    }

    private KernelBootSequence sequencePour(Path repertoire) {
        PluginLifecycleManager manager = new PluginLifecycleManager(
                new ManifestReader(), new ExtensionRegistry(), new FakePluginLoader());
        return new KernelBootSequence(new PluginDirectoryScanner(), manager, repertoire);
    }

    @Test
    void anEmptyPluginsDirectoryProducesAnEmptyReport(@TempDir Path repertoire) {
        RapportDemarrage rapport = sequencePour(repertoire).demarrer();

        assertEquals(0, rapport.candidatsTrouves());
        assertTrue(rapport.modulesCharges().isEmpty());
        assertTrue(rapport.echecs().isEmpty());
    }

    @Test
    void aCandidateWithAValidManifestLoadsSuccessfully(@TempDir Path repertoire) throws IOException {
        creerCandidat(repertoire, "academie", "1.0.0");

        RapportDemarrage rapport = sequencePour(repertoire).demarrer();

        assertEquals(1, rapport.candidatsTrouves());
        assertEquals(1, rapport.modulesCharges().size());
        assertEquals("academie", rapport.modulesCharges().get(0));
        assertTrue(rapport.echecs().isEmpty());
    }

    @Test
    void aCandidateWithAnIncompleteManifestIsCountedAsAFailureNeverThrown(@TempDir Path repertoire)
            throws IOException {
        Files.writeString(repertoire.resolve("casse.jar"), "contenu factice");
        Files.writeString(repertoire.resolve("casse.properties"), "moduleId=casse\n"); // version manquante

        RapportDemarrage rapport = sequencePour(repertoire).demarrer();

        assertEquals(1, rapport.candidatsTrouves());
        assertTrue(rapport.modulesCharges().isEmpty());
        assertEquals(1, rapport.echecs().size());
    }
}
