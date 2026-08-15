package africa.civitas.egen.kernel.moduleregistry.application;

import africa.civitas.egen.kernel.domain.module.Activation;
import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.moduleregistry.command.ActiverModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.DesactiverModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.EnregistrerModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.SouscrireModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.exception.ActivationDejaActiveException;
import africa.civitas.egen.kernel.moduleregistry.exception.ActivationIntrouvableException;
import africa.civitas.egen.kernel.moduleregistry.exception.ModuleIntrouvableAuCatalogueException;
import africa.civitas.egen.kernel.moduleregistry.exception.SouscriptionRequiseException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ActivationServiceImplTest {

    @Inject
    CatalogueServiceImpl catalogueService;

    @Inject
    SouscriptionServiceImpl souscriptionService;

    @Inject
    ActivationServiceImpl activationService;

    private ModuleId enregistrerModule(String moduleId) {
        ModuleId id = new ModuleId(moduleId);
        catalogueService.enregistrer(new EnregistrerModuleCommand(
                id, moduleId, "description", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        return id;
    }

    @Test
    @TestTransaction
    void activatingAModuleAbsentFromTheCatalogueIsRejected() {
        assertThrows(ModuleIntrouvableAuCatalogueException.class, () -> activationService.activer(
                new ActiverModuleCommand(UUID.randomUUID(), UUID.randomUUID(), new ModuleId("inexistant"),
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void activatingACatalogedButUnsubscribedModuleIsRejected() {
        ModuleId moduleId = enregistrerModule("academie");

        assertThrows(SouscriptionRequiseException.class, () -> activationService.activer(
                new ActiverModuleCommand(UUID.randomUUID(), UUID.randomUUID(), moduleId,
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void activatingASubscribedModuleSucceeds() {
        ModuleId moduleId = enregistrerModule("academie");
        UUID contexteSouscripteurId = UUID.randomUUID();
        UUID contexteCibleId = UUID.randomUUID();
        souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteSouscripteurId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        Activation activation = activationService.activer(new ActiverModuleCommand(
                contexteSouscripteurId, contexteCibleId, moduleId, Acteur.systeme("test"),
                OrigineDonnee.SAISIE_MANUELLE));

        assertTrue(activation.active());
        assertEquals(contexteCibleId, activation.contexteId());
    }

    @Test
    @TestTransaction
    void aSecondContexteOfAnUnsubscribedContexteSouscripteurCannotActivateEvenIfAnotherDid() {
        ModuleId moduleId = enregistrerModule("academie");
        UUID contexteSouscripteurAbonne = UUID.randomUUID();
        souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteSouscripteurAbonne, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        UUID autreContexteSouscripteur = UUID.randomUUID();
        UUID contexteCibleDeLAutre = UUID.randomUUID();

        assertThrows(SouscriptionRequiseException.class, () -> activationService.activer(
                new ActiverModuleCommand(autreContexteSouscripteur, contexteCibleDeLAutre, moduleId,
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void activatingTwiceForTheSameContexteIsRejected() {
        ModuleId moduleId = enregistrerModule("academie");
        UUID contexteSouscripteurId = UUID.randomUUID();
        UUID contexteCibleId = UUID.randomUUID();
        souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteSouscripteurId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        activationService.activer(new ActiverModuleCommand(
                contexteSouscripteurId, contexteCibleId, moduleId, Acteur.systeme("test"),
                OrigineDonnee.SAISIE_MANUELLE));

        assertThrows(ActivationDejaActiveException.class, () -> activationService.activer(
                new ActiverModuleCommand(contexteSouscripteurId, contexteCibleId, moduleId, Acteur.systeme("test"),
                        OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void deactivatingMakesTheActivationInactiveButKeepsItInHistory() {
        ModuleId moduleId = enregistrerModule("academie");
        UUID contexteSouscripteurId = UUID.randomUUID();
        UUID contexteCibleId = UUID.randomUUID();
        souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteSouscripteurId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Activation activation = activationService.activer(new ActiverModuleCommand(
                contexteSouscripteurId, contexteCibleId, moduleId, Acteur.systeme("test"),
                OrigineDonnee.SAISIE_MANUELLE));

        activationService.desactiver(new DesactiverModuleCommand(
                activation.id(), Acteur.systeme("test"), "module retire"));

        List<Activation> historique = activationService.listerPourContexte(contexteCibleId);
        assertEquals(1, historique.size());
        assertFalse(historique.get(0).active());
    }

    @Test
    @TestTransaction
    void deactivatingAnUnknownActivationIsRejected() {
        assertThrows(ActivationIntrouvableException.class, () -> activationService.desactiver(
                new DesactiverModuleCommand(UUID.randomUUID(), Acteur.systeme("test"), "motif")));
    }
}
