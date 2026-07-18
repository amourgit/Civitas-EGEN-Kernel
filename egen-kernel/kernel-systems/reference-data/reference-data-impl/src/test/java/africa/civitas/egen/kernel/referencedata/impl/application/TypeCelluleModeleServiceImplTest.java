package africa.civitas.egen.kernel.referencedata.impl.application;

import africa.civitas.egen.kernel.referencedata.api.command.AjouterTypeCelluleModeleCommand;
import africa.civitas.egen.kernel.referencedata.api.command.CreerModeleSectorielCommand;
import africa.civitas.egen.kernel.referencedata.api.domain.ModeleSectoriel;
import africa.civitas.egen.kernel.referencedata.api.domain.TypeCelluleModele;
import africa.civitas.egen.kernel.referencedata.api.exception.ModeleSectorielIntrouvableException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TypeCelluleModeleServiceImplTest {

    @Inject
    ModeleSectorielServiceImpl modeleSectorielService;

    @Inject
    TypeCelluleModeleServiceImpl service;

    private ModeleSectoriel unModele() {
        return modeleSectorielService.creer(new CreerModeleSectorielCommand(
                "Education superieure", "Gabarit universites", "1.0.0",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void ajouterARootTypeSucceeds() {
        ModeleSectoriel m = unModele();

        TypeCelluleModele racine = service.ajouter(new AjouterTypeCelluleModeleCommand(
                m.id(), "Etablissement", 0, null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertTrue(racine.typeParentSuggere().isEmpty());
    }

    @Test
    @TestTransaction
    void ajouterAChildTypeWithValidParentSucceeds() {
        ModeleSectoriel m = unModele();
        TypeCelluleModele racine = service.ajouter(new AjouterTypeCelluleModeleCommand(
                m.id(), "Etablissement", 0, null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        TypeCelluleModele enfant = service.ajouter(new AjouterTypeCelluleModeleCommand(
                m.id(), "Faculte", 1, racine.id(), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(racine.id(), enfant.typeParentSuggere().orElseThrow());
    }

    @Test
    @TestTransaction
    void ajouterRejectsAnUnknownModeleSectoriel() {
        assertThrows(ModeleSectorielIntrouvableException.class, () -> service.ajouter(
                new AjouterTypeCelluleModeleCommand(
                        UUID.randomUUID(), "Faculte", 1, null,
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void ajouterRejectsAParentFromADifferentModeleSectoriel() {
        ModeleSectoriel m1 = unModele();
        ModeleSectoriel m2 = modeleSectorielService.creer(new CreerModeleSectorielCommand(
                "Sante", "Gabarit sante", "1.0.0", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCelluleModele racineM1 = service.ajouter(new AjouterTypeCelluleModeleCommand(
                m1.id(), "Etablissement", 0, null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertThrows(IllegalArgumentException.class, () -> service.ajouter(
                new AjouterTypeCelluleModeleCommand(
                        m2.id(), "Service", 1, racineM1.id(),
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }
}
