package africa.civitas.egen.kernel.moduleregistry.application;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.domain.module.Souscription;
import africa.civitas.egen.kernel.moduleregistry.command.EnregistrerModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.ResilierSouscriptionCommand;
import africa.civitas.egen.kernel.moduleregistry.command.SouscrireModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.exception.ModuleIntrouvableAuCatalogueException;
import africa.civitas.egen.kernel.moduleregistry.exception.SouscriptionDejaActiveException;
import africa.civitas.egen.kernel.moduleregistry.exception.SouscriptionIntrouvableException;
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
class SouscriptionServiceImplTest {

    @Inject
    CatalogueServiceImpl catalogueService;

    @Inject
    SouscriptionServiceImpl souscriptionService;

    private ModuleId enregistrerModule(String moduleId) {
        ModuleId id = new ModuleId(moduleId);
        catalogueService.enregistrer(new EnregistrerModuleCommand(
                id, moduleId, "description", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        return id;
    }

    @Test
    @TestTransaction
    void subscribingToAModuleAbsentFromTheCatalogueIsRejected() {
        UUID contexteId = UUID.randomUUID();

        assertThrows(ModuleIntrouvableAuCatalogueException.class, () -> souscriptionService.souscrire(
                new SouscrireModuleCommand(contexteId, new ModuleId("inexistant"),
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void subscribingToACatalogedModuleSucceeds() {
        ModuleId moduleId = enregistrerModule("academie");
        UUID contexteId = UUID.randomUUID();

        Souscription souscription = souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertTrue(souscription.active());
        assertTrue(souscriptionService.estActivePour(contexteId, moduleId));
    }

    @Test
    @TestTransaction
    void subscribingTwiceToTheSameModuleForTheSameContexteIsRejected() {
        ModuleId moduleId = enregistrerModule("academie");
        UUID contexteId = UUID.randomUUID();
        souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertThrows(SouscriptionDejaActiveException.class, () -> souscriptionService.souscrire(
                new SouscrireModuleCommand(contexteId, moduleId, Acteur.systeme("test"),
                        OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void cancellingASubscriptionMakesItInactiveButKeepsItInHistory() {
        ModuleId moduleId = enregistrerModule("academie");
        UUID contexteId = UUID.randomUUID();
        Souscription souscription = souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        souscriptionService.resilier(new ResilierSouscriptionCommand(
                souscription.id(), Acteur.systeme("test"), "fin de contrat"));

        assertFalse(souscriptionService.estActivePour(contexteId, moduleId));
        List<Souscription> historique = souscriptionService.listerPourContexte(contexteId);
        assertEquals(1, historique.size());
        assertFalse(historique.get(0).active());
    }

    @Test
    @TestTransaction
    void cancellingAnUnknownSubscriptionIsRejected() {
        assertThrows(SouscriptionIntrouvableException.class, () -> souscriptionService.resilier(
                new ResilierSouscriptionCommand(UUID.randomUUID(), Acteur.systeme("test"), "motif")));
    }

    @Test
    @TestTransaction
    void aContexteCanResubscribeAfterCancelling() {
        ModuleId moduleId = enregistrerModule("academie");
        UUID contexteId = UUID.randomUUID();
        Souscription premiere = souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        souscriptionService.resilier(new ResilierSouscriptionCommand(
                premiere.id(), Acteur.systeme("test"), "resiliation"));

        Souscription seconde = souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertTrue(seconde.active());
        assertEquals(2, souscriptionService.listerPourContexte(contexteId).size());
    }
}
