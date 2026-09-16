package africa.civitas.egen.kernel.pluginengine.lifecycle;

import africa.civitas.egen.kernel.pluginengine.loader.CandidatModule;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReadException;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReader;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestSource;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionDecouverte;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionRegistry;
import africa.civitas.egen.kernel.pluginengine.testsupport.FakePluginLoader;
import africa.civitas.egen.kernel.pluginengine.testsupport.ImplementationDeTest;
import africa.civitas.egen.kernel.pluginengine.testsupport.PointExtensionDeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Couverture complete du seul point d'entree du mode d'extension embarque,
 * entierement sans Quarkus ni Docker : {@link PluginLifecycleManager} est instancie
 * a la main, avec une doublure pour le seul collaborateur physique
 * ({@link FakePluginLoader}) — la preuve, par la structure meme de ce test, que la
 * logique de decision est independante de tout mecanisme physique concret.
 */
class PluginLifecycleManagerTest {

    private ExtensionRegistry extensionRegistry;
    private FakePluginLoader pluginLoader;
    private PluginLifecycleManager manager;

    @BeforeEach
    void setUp() {
        extensionRegistry = new ExtensionRegistry();
        pluginLoader = new FakePluginLoader();
        manager = new PluginLifecycleManager(new ManifestReader(), extensionRegistry, pluginLoader);
    }

    private static ManifestSource manifesteValide(String moduleId, String... dependances) {
        Map<String, String> donnees = new HashMap<>();
        donnees.put("moduleId", moduleId);
        donnees.put("version", "1.0.0");
        if (dependances.length > 0) {
            donnees.put("dependencies", String.join(",", dependances));
        }
        return () -> donnees;
    }

    private CandidatModule candidat(String moduleId, String... dependances) {
        return new CandidatModule(Path.of(moduleId + ".jar"), manifesteValide(moduleId, dependances));
    }

    // --- charger() : ordre 1 — lecture et validation du Manifeste ---

    @Test
    void refusesToLoadWhenTheManifestSourceFailsToRead() {
        ManifestSource sourceEnEchec = () -> {
            throw new ManifestReadException("fichier absent");
        };
        CandidatModule candidatCasse = new CandidatModule(Path.of("casse.jar"), sourceEnEchec);

        ResultatChargement resultat = manager.charger(candidatCasse);

        assertFalse(resultat.reussi());
        assertTrue(((ResultatChargement.Echec) resultat).motif().contains("Manifeste"));
    }

    @Test
    void refusesToLoadWhenTheManifestIsStructurallyInvalid() {
        Map<String, String> donneesInvalides = Map.of("moduleId", "Module_Invalide", "version", "1.0.0");
        CandidatModule candidatInvalide = new CandidatModule(
                Path.of("invalide.jar"), () -> donneesInvalides);

        ResultatChargement resultat = manager.charger(candidatInvalide);

        assertFalse(resultat.reussi());
    }

    // --- charger() : idempotence ---

    @Test
    void refusesToLoadAModuleThatIsAlreadyLoaded() {
        manager.charger(candidat("academie"));

        ResultatChargement second = manager.charger(candidat("academie"));

        assertFalse(second.reussi());
    }

    // --- charger() : ordre 2 — dependances ---

    @Test
    void refusesToLoadAModuleWhoseDependenciesAreNotYetLoaded() {
        ResultatChargement resultat = manager.charger(candidat("reporting", "identite"));

        assertFalse(resultat.reussi());
        assertTrue(((ResultatChargement.Echec) resultat).motif().contains("identite"));
    }

    @Test
    void loadsSuccessfullyOnceEveryDependencyIsAlreadyLoaded() {
        pluginLoader.marquerCharge("identite");

        ResultatChargement resultat = manager.charger(candidat("reporting", "identite"));

        assertTrue(resultat.reussi());
    }

    // --- charger() : succes complet ---

    @Test
    void loadingSuccessfullyRegistersTheDiscoveredExtensionsAndTracksTheManifest() {
        pluginLoader.avecExtensionsPour("academie", List.of(
                new ExtensionDecouverte(PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "academie")));

        ResultatChargement resultat = manager.charger(candidat("academie"));

        assertTrue(resultat instanceof ResultatChargement.Succes);
        ResultatChargement.Succes succes = (ResultatChargement.Succes) resultat;
        assertEquals(1, succes.extensionsEnregistrees());
        assertEquals("academie", succes.manifeste().moduleId());
        assertTrue(manager.modulesCharges().contains("academie"));
        assertEquals("academie", manager.manifestePour("academie").orElseThrow().moduleId());
        assertTrue(pluginLoader.estCharge("academie"));
    }

    // --- decharger() ---

    @Test
    void refusesToUnloadAModuleThatIsNotLoaded() {
        ResultatDechargement resultat = manager.decharger("inconnu");

        assertFalse(resultat.reussi());
    }

    @Test
    void refusesToUnloadAModuleThatAnotherLoadedModuleStillDependsOn() {
        pluginLoader.marquerCharge("identite");
        manager.charger(candidat("identite"));
        manager.charger(candidat("reporting", "identite"));

        ResultatDechargement resultat = manager.decharger("identite");

        assertFalse(resultat.reussi());
        assertTrue(((ResultatDechargement.Echec) resultat).motif().contains("reporting"));
    }

    @Test
    void unloadingSuccessfullyRemovesTrackedExtensionsAndForgetsTheManifest() {
        pluginLoader.avecExtensionsPour("academie", List.of(
                new ExtensionDecouverte(PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "academie")));
        manager.charger(candidat("academie"));

        ResultatDechargement resultat = manager.decharger("academie");

        assertTrue(resultat instanceof ResultatDechargement.Succes);
        assertEquals(1, ((ResultatDechargement.Succes) resultat).extensionsRetirees());
        assertFalse(manager.modulesCharges().contains("academie"));
        assertFalse(pluginLoader.estCharge("academie"));
        assertTrue(manager.manifestePour("academie").isEmpty());
    }

    // --- enregistrerExtensionManuelle() ---

    @Test
    void manuallyRegisteringAnExtensionMakesItAvailableThroughTheRegistry() {
        ExtensionDecouverte decouverte = new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "academie");

        manager.enregistrerExtensionManuelle(decouverte);

        assertEquals(1, extensionRegistry.obtenir(PointExtensionDeTest.class).size());
    }

    @Test
    void rejectsANullDecouverteOnManualRegistration() {
        assertThrows(IllegalArgumentException.class, () -> manager.enregistrerExtensionManuelle(null));
    }

    // --- validation des arguments ---

    @Test
    void rejectsANullCandidateOnLoad() {
        assertThrows(IllegalArgumentException.class, () -> manager.charger(null));
    }

    @Test
    void rejectsANullModuleIdOnUnload() {
        assertThrows(IllegalArgumentException.class, () -> manager.decharger(null));
    }

    @Test
    void constructorRejectsAnyNullCollaborator() {
        assertThrows(IllegalArgumentException.class, () -> new PluginLifecycleManager(
                null, extensionRegistry, pluginLoader));
    }
}
