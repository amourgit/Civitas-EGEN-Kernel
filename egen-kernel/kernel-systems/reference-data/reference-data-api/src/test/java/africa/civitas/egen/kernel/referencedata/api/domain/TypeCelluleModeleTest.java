package africa.civitas.egen.kernel.referencedata.api.domain;

import africa.civitas.egen.kernel.referencedata.api.command.AjouterTypeCelluleModeleCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeCelluleModeleTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidRootType() {
        TypeCelluleModele t = new TypeCelluleModele(
                UUID.randomUUID(), UUID.randomUUID(), "Etablissement", 0, null, TRACABILITE);

        assertTrue(t.typeParentSuggere().isEmpty());
    }

    @Test
    void buildsAValidChildType() {
        TypeCelluleModele t = new TypeCelluleModele(
                UUID.randomUUID(), UUID.randomUUID(), "Faculte", 1, UUID.randomUUID(), TRACABILITE);

        assertTrue(t.typeParentSuggere().isPresent());
    }

    @Test
    void rejectsBeingItsOwnParent() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new TypeCelluleModele(
                id, UUID.randomUUID(), "Faculte", 1, id, TRACABILITE));
    }

    @Test
    void rejectsANegativeNiveauIndicatif() {
        assertThrows(IllegalArgumentException.class, () -> new TypeCelluleModele(
                UUID.randomUUID(), UUID.randomUUID(), "Faculte", -1, null, TRACABILITE));
    }

    @Test
    void commandeRejectsAMissingModeleSectorialId() {
        assertThrows(NullPointerException.class, () -> new AjouterTypeCelluleModeleCommand(
                null, "Faculte", 1, null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
