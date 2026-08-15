package africa.civitas.egen.kernel.pluginengine.lifecycle;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.policy.PolitiqueNoyauImpl;
import africa.civitas.egen.kernel.pluginengine.loader.CandidatModule;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReadException;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReader;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestSource;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionDecouverte;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionRegistry;
import africa.civitas.egen.kernel.testsupport.fake.FakeKernelPermissionCheck;
import africa.civitas.egen.kernel.testsupport.fake.FakeModuleActivationResolver;
import africa.civitas.egen.kernel.pluginengine.testsupport.FakePluginLoader;
import africa.civitas.egen.kernel.pluginengine.testsupport.ImplementationDeTest;
import africa.civitas.egen.kernel.pluginengine.testsupport.PointExtensionDeTest;
import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Couverture complete du seul point d'entree du moteur de plugins, entierement sans
 * Quarkus ni Docker : {@link PluginLifecycleManager} est instancie a la main, avec
 * une {@link PolitiqueNoyauImpl} reelle (aucun etat, aucune dependance externe) et
 * des doublures pour les trois autres collaborateurs — la preuve, par la structure
 * meme de ce test, que la logique de decision est independante de tout mecanisme
 * physique concret.
 */
class PluginLifecycleManagerTest {

    private static final UUID CONTEXTE = UUID.randomUUID();
    private static final KernelSubject ADMIN = KernelSubject.nouveau();
    private static final KernelSubject SANS_DROITS = KernelSubject.nouveau();

    private FakeKernelPermissionCheck permissionCheck;
    private FakeModuleActivationResolver activationResolver;
    private FakePluginLoader pluginLoader;
    private PluginLifecycleManager manager;

    @BeforeEach
    void setUp() {
        permissionCheck = new FakeKernelPermissionCheck()
                .autoriser(ADMIN, KernelCapability.CHARGER_MODULE)
                .autoriser(ADMIN, KernelCapability.DECHARGER_MODULE)
                .autoriser(ADMIN, KernelCapability.ENREGISTRER_EXTENSION);
        activationResolver = new FakeModuleActivationResolver();
        pluginLoader = new FakePluginLoader();
        manager = new PluginLifecycleManager(
                permissionCheck, activationResolver, new PolitiqueNoyauImpl(),
                new ManifestReader(), new ExtensionRegistry(), pluginLoader);
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

    // --- charger() : ordre 1 — capacite administrative ---

    @Test
    void refusesToLoadWhenTheRequestingSubjectLacksTheChargerModuleCapacity() {
        activationResolver.activerPour(CONTEXTE, new ModuleId("academie"));

        ResultatChargement resultat = manager.charger(candidat("academie"), SANS_DROITS, CONTEXTE);

        assertFalse(resultat.reussi());
        assertTrue(pluginLoader.modulesCharges().isEmpty());
    }

    @Test
    void theBootstrapSubjectCanAlwaysLoadEvenWithoutAnyExplicitGrant() {
        activationResolver.activerPour(CONTEXTE, new ModuleId("academie"));

        ResultatChargement resultat = manager.charger(candidat("academie"), KernelSubject.sujetBootstrap(), CONTEXTE);

        assertTrue(resultat.reussi());
    }

    // --- charger() : ordre 2 — lecture et validation du Manifeste ---

    @Test
    void refusesToLoadWhenTheManifestSourceFailsToRead() {
        ManifestSource sourceEnEchec = () -> {
            throw new ManifestReadException("fichier absent");
        };
        CandidatModule candidatCasse = new CandidatModule(Path.of("casse.jar"), sourceEnEchec);

        ResultatChargement resultat = manager.charger(candidatCasse, ADMIN, CONTEXTE);

        assertFalse(resultat.reussi());
        assertTrue(((ResultatChargement.Echec) resultat).motif().contains("Manifeste"));
    }

    @Test
    void refusesToLoadWhenTheManifestIsStructurallyInvalid() {
        Map<String, String> donneesInvalides = Map.of("moduleId", "Module_Invalide", "version", "1.0.0");
        CandidatModule candidatInvalide = new CandidatModule(
                Path.of("invalide.jar"), () -> donneesInvalides);

        ResultatChargement resultat = manager.charger(candidatInvalide, ADMIN, CONTEXTE);

        assertFalse(resultat.reussi());
    }

    // --- charger() : idempotence ---

    @Test
    void refusesToLoadAModuleThatIsAlreadyLoaded() {
        activationResolver.activerPour(CONTEXTE, new ModuleId("academie"));
        manager.charger(candidat("academie"), ADMIN, CONTEXTE);

        ResultatChargement second = manager.charger(candidat("academie"), ADMIN, CONTEXTE);

        assertFalse(second.reussi());
    }

    // --- charger() : ordre 3 — Activation ---

    @Test
    void refusesToLoadAModuleThatHasNoActiveActivationForTheTargetContexte() {
        ResultatChargement resultat = manager.charger(candidat("academie"), ADMIN, CONTEXTE);

        assertFalse(resultat.reussi());
        assertTrue(pluginLoader.modulesCharges().isEmpty());
    }

    // --- charger() : ordre 4 — dependances ---

    @Test
    void refusesToLoadAModuleWhoseDependenciesAreNotYetLoaded() {
        activationResolver.activerPour(CONTEXTE, new ModuleId("reporting"));

        ResultatChargement resultat = manager.charger(candidat("reporting", "identite"), ADMIN, CONTEXTE);

        assertFalse(resultat.reussi());
        assertTrue(((ResultatChargement.Echec) resultat).motif().contains("identite"));
    }

    @Test
    void loadsSuccessfullyOnceEveryDependencyIsAlreadyLoaded() {
        pluginLoader.marquerCharge("identite");
        activationResolver.activerPour(CONTEXTE, new ModuleId("reporting"));

        ResultatChargement resultat = manager.charger(candidat("reporting", "identite"), ADMIN, CONTEXTE);

        assertTrue(resultat.reussi());
    }

    // --- charger() : succes complet ---

    @Test
    void loadingSuccessfullyRegistersTheDiscoveredExtensionsAndTracksTheManifest() {
        activationResolver.activerPour(CONTEXTE, new ModuleId("academie"));
        pluginLoader.avecExtensionsPour("academie", List.of(
                new ExtensionDecouverte(PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "academie")));

        ResultatChargement resultat = manager.charger(candidat("academie"), ADMIN, CONTEXTE);

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
    void refusesToUnloadWhenTheRequestingSubjectLacksTheDechargerModuleCapacity() {
        activationResolver.activerPour(CONTEXTE, new ModuleId("academie"));
        manager.charger(candidat("academie"), ADMIN, CONTEXTE);

        ResultatDechargement resultat = manager.decharger("academie", SANS_DROITS);

        assertFalse(resultat.reussi());
        assertTrue(manager.modulesCharges().contains("academie"));
    }

    @Test
    void refusesToUnloadAModuleThatIsNotLoaded() {
        ResultatDechargement resultat = manager.decharger("inconnu", ADMIN);

        assertFalse(resultat.reussi());
    }

    @Test
    void refusesToUnloadAModuleThatAnotherLoadedModuleStillDependsOn() {
        pluginLoader.marquerCharge("identite");
        activationResolver
                .activerPour(CONTEXTE, new ModuleId("identite"))
                .activerPour(CONTEXTE, new ModuleId("reporting"));
        manager.charger(candidat("identite"), ADMIN, CONTEXTE);
        manager.charger(candidat("reporting", "identite"), ADMIN, CONTEXTE);

        ResultatDechargement resultat = manager.decharger("identite", ADMIN);

        assertFalse(resultat.reussi());
        assertTrue(((ResultatDechargement.Echec) resultat).motif().contains("reporting"));
    }

    @Test
    void unloadingSuccessfullyRemovesTrackedExtensionsAndForgetsTheManifest() {
        activationResolver.activerPour(CONTEXTE, new ModuleId("academie"));
        pluginLoader.avecExtensionsPour("academie", List.of(
                new ExtensionDecouverte(PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "academie")));
        manager.charger(candidat("academie"), ADMIN, CONTEXTE);

        ResultatDechargement resultat = manager.decharger("academie", ADMIN);

        assertTrue(resultat instanceof ResultatDechargement.Succes);
        assertEquals(1, ((ResultatDechargement.Succes) resultat).extensionsRetirees());
        assertFalse(manager.modulesCharges().contains("academie"));
        assertFalse(pluginLoader.estCharge("academie"));
        assertTrue(manager.manifestePour("academie").isEmpty());
    }

    // --- enregistrerExtensionManuelle() ---

    @Test
    void manuallyRegisteringAnExtensionRequiresTheEnregistrerExtensionCapacity() {
        ExtensionDecouverte decouverte = new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "academie");

        DecisionNoyau decision = manager.enregistrerExtensionManuelle(decouverte, SANS_DROITS);

        assertFalse(decision.autorise());
    }

    @Test
    void manuallyRegisteringAnExtensionSucceedsForAnAuthorizedSubject() {
        ExtensionDecouverte decouverte = new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "academie");

        DecisionNoyau decision = manager.enregistrerExtensionManuelle(decouverte, ADMIN);

        assertTrue(decision.autorise());
    }

    // --- validation des arguments ---

    @Test
    void rejectsANullCandidateOnLoad() {
        assertThrows(IllegalArgumentException.class, () -> manager.charger(null, ADMIN, CONTEXTE));
    }

    @Test
    void rejectsANullTargetContexteOnLoad() {
        assertThrows(IllegalArgumentException.class, () -> manager.charger(candidat("academie"), ADMIN, null));
    }

    @Test
    void rejectsANullModuleIdOnUnload() {
        assertThrows(IllegalArgumentException.class, () -> manager.decharger(null, ADMIN));
    }

    @Test
    void constructorRejectsAnyNullCollaborator() {
        assertThrows(IllegalArgumentException.class, () -> new PluginLifecycleManager(
                null, activationResolver, new PolitiqueNoyauImpl(),
                new ManifestReader(), new ExtensionRegistry(), pluginLoader));
    }
}
