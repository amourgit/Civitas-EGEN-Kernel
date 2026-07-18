package africa.civitas.egen.kernel.referencedata.impl.application;

import africa.civitas.egen.kernel.referencedata.api.command.AjouterMandatModeleCommand;
import africa.civitas.egen.kernel.referencedata.api.command.CreerModeleSectorielCommand;
import africa.civitas.egen.kernel.referencedata.api.domain.MandatModele;
import africa.civitas.egen.kernel.referencedata.api.domain.ModeleSectoriel;
import africa.civitas.egen.kernel.referencedata.api.exception.ModeleSectorielIntrouvableException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class MandatModeleServiceImplTest {

    @Inject
    ModeleSectorielServiceImpl modeleSectorielService;

    @Inject
    MandatModeleServiceImpl service;

    @Test
    @TestTransaction
    void ajouterPersistsAndReturnsTheMandatModele() {
        ModeleSectoriel m = modeleSectorielService.creer(new CreerModeleSectorielCommand(
                "Education superieure", "Gabarit universites", "1.0.0",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        MandatModele mm = service.ajouter(new AjouterMandatModeleCommand(
                m.id(), "Enseignant", 1, "Dispense les cours",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(m.id(), mm.modeleSectorialId());
    }

    @Test
    @TestTransaction
    void ajouterRejectsAnUnknownModeleSectoriel() {
        assertThrows(ModeleSectorielIntrouvableException.class, () -> service.ajouter(
                new AjouterMandatModeleCommand(UUID.randomUUID(), "Enseignant", 1, "Description",
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void listerParModeleSectorielReturnsOnlyMatchingEntries() {
        ModeleSectoriel m = modeleSectorielService.creer(new CreerModeleSectorielCommand(
                "Education superieure", "Gabarit universites", "1.0.0",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        service.ajouter(new AjouterMandatModeleCommand(
                m.id(), "Enseignant", 1, "Dispense les cours",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        service.ajouter(new AjouterMandatModeleCommand(
                m.id(), "Directeur", 5, "Dirige l'etablissement",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        List<MandatModele> mandats = service.listerParModeleSectoriel(m.id());

        assertEquals(2, mandats.size());
    }
}
