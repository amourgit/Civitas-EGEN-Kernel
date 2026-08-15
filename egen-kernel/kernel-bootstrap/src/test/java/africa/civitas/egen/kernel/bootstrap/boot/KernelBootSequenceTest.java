package africa.civitas.egen.kernel.bootstrap.boot;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.moduleregistry.command.ActiverModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.EnregistrerModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.SouscrireModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.service.ActivationService;
import africa.civitas.egen.kernel.moduleregistry.service.CatalogueService;
import africa.civitas.egen.kernel.moduleregistry.service.ModuleActivationResolver;
import africa.civitas.egen.kernel.moduleregistry.service.SouscriptionService;
import africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReader;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionRegistry;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelPermissionCheck;
import africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyau;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.io.TempDir;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie {@link KernelBootSequence} contre de VRAIES implementations
 * (KernelPermissionCheckImpl, ModuleActivationResolverImpl, PolitiqueNoyauImpl —
 * injectees, avec Testcontainers pour les deux premieres), avec {@link
 * FakePluginLoader} pour le seul maillon qui necessiterait un plugin JAR physique.
 * Le sujet bootstrap n'a besoin d'aucun octroi explicite (voir
 * KernelPermissionCheckImpl) : seule l'Activation reelle du module distingue les
 * scenarios ci-dessous.
 */
@QuarkusTest
class KernelBootSequenceTest {

    @Inject
    KernelPermissionCheck kernelPermissionCheck;

    @Inject
    ModuleActivationResolver moduleActivationResolver;

    @Inject
    PolitiqueNoyau politiqueNoyau;

    @Inject
    CatalogueService catalogueService;

    @Inject
    SouscriptionService souscriptionService;

    @Inject
    ActivationService activationService;

    private void creerCandidat(Path repertoire, String moduleId, String version) throws IOException {
        Files.writeString(repertoire.resolve(moduleId + ".jar"), "contenu factice, jamais lu par FakePluginLoader");
        Files.writeString(repertoire.resolve(moduleId + ".properties"),
                "moduleId=" + moduleId + "\nversion=" + version + "\n");
    }

    private void activerReellement(UUID contexteRacine, ModuleId moduleId) {
        catalogueService.enregistrer(new EnregistrerModuleCommand(
                moduleId, moduleId.valeur(), "module de test", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        UUID contexteSouscripteurId = UUID.randomUUID();
        souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteSouscripteurId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        activationService.activer(new ActiverModuleCommand(
                contexteSouscripteurId, contexteRacine, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    private KernelBootSequence sequencePour(Path repertoire, UUID contexteRacine) {
        PluginLifecycleManager manager = new PluginLifecycleManager(
                kernelPermissionCheck, moduleActivationResolver, politiqueNoyau,
                new ManifestReader(), new ExtensionRegistry(), new FakePluginLoader());
        return new KernelBootSequence(new PluginDirectoryScanner(), manager, repertoire, contexteRacine);
    }

    @Test
    @TestTransaction
    void anEmptyPluginsDirectoryProducesAnEmptyReport(@TempDir Path repertoire) {
        RapportDemarrage rapport = sequencePour(repertoire, UUID.randomUUID()).demarrer();

        assertEquals(0, rapport.candidatsTrouves());
        assertTrue(rapport.modulesCharges().isEmpty());
        assertTrue(rapport.echecs().isEmpty());
    }

    @Test
    @TestTransaction
    void aCandidateWithoutAnyActivationIsRefusedNotLoaded(@TempDir Path repertoire)
            throws IOException {
        creerCandidat(repertoire, "academie", "1.0.0");

        RapportDemarrage rapport = sequencePour(repertoire, UUID.randomUUID()).demarrer();

        assertEquals(1, rapport.candidatsTrouves());
        assertTrue(rapport.modulesCharges().isEmpty());
        assertEquals(1, rapport.echecs().size());
    }

    @Test
    @TestTransaction
    void aCandidateWithARealActiveActivationLoadsSuccessfully(@TempDir Path repertoire)
            throws IOException {
        UUID contexteRacine = UUID.randomUUID();
        ModuleId moduleId = new ModuleId("academie");
        activerReellement(contexteRacine, moduleId);
        creerCandidat(repertoire, "academie", "1.0.0");

        RapportDemarrage rapport = sequencePour(repertoire, contexteRacine).demarrer();

        assertEquals(1, rapport.candidatsTrouves());
        assertEquals(1, rapport.modulesCharges().size());
        assertEquals("academie", rapport.modulesCharges().get(0));
        assertTrue(rapport.echecs().isEmpty());
    }

    @Test
    @TestTransaction
    void aCandidateWithAnIncompleteManifestIsCountedAsAFailureNeverThrown(@TempDir Path repertoire)
            throws IOException {
        Files.writeString(repertoire.resolve("casse.jar"), "contenu factice");
        Files.writeString(repertoire.resolve("casse.properties"), "moduleId=casse\n"); // version manquante

        RapportDemarrage rapport = sequencePour(repertoire, UUID.randomUUID()).demarrer();

        assertEquals(1, rapport.candidatsTrouves());
        assertTrue(rapport.modulesCharges().isEmpty());
        assertEquals(1, rapport.echecs().size());
    }
}
