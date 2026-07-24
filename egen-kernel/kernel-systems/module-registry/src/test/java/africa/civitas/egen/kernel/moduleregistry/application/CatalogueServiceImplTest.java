package africa.civitas.egen.kernel.moduleregistry.application;

import africa.civitas.egen.kernel.domain.module.CatalogueEntree;
import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.moduleregistry.command.EnregistrerModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.exception.ModuleDejaAuCatalogueException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CatalogueServiceImplTest {

    @Inject
    CatalogueServiceImpl service;

    @Test
    @TestTransaction
    void registeringANewModuleMakesItFindableInTheCatalogue() {
        ModuleId moduleId = new ModuleId("academie");

        CatalogueEntree entree = service.enregistrer(new EnregistrerModuleCommand(
                moduleId, "Academie", "Gestion academique", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(moduleId, entree.moduleId());
        assertTrue(service.estAuCatalogue(moduleId));
    }

    @Test
    @TestTransaction
    void aModuleNotYetRegisteredIsNotInTheCatalogue() {
        assertFalse(service.estAuCatalogue(new ModuleId("inexistant")));
    }

    @Test
    @TestTransaction
    void registeringTheSameModuleTwiceIsRejected() {
        ModuleId moduleId = new ModuleId("rh");
        service.enregistrer(new EnregistrerModuleCommand(
                moduleId, "RH", "Ressources humaines", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertThrows(ModuleDejaAuCatalogueException.class, () -> service.enregistrer(new EnregistrerModuleCommand(
                moduleId, "RH bis", "Doublon", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }
}
