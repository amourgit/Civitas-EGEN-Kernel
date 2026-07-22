package africa.civitas.egen.modules.business.organization.api.domain;

import africa.civitas.egen.modules.business.organization.api.command.CreerTypeCelluleCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeCelluleTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidTypeCelluleWithoutConstraint() {
        TypeCellule t = new TypeCellule(
                UUID.randomUUID(), UUID.randomUUID(), "Etablissement", "Racine", 0,
                null, null, StatutTypeCellule.ACTIF, TRACABILITE);

        assertTrue(t.sansContrainteDeParent());
    }

    @Test
    void rejectsBeingItsOwnAllowedParent() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new TypeCellule(
                id, UUID.randomUUID(), "Faculte", "Description", 1,
                List.of(id), null, StatutTypeCellule.ACTIF, TRACABILITE));
    }

    @Test
    void rejectsANegativeNiveauIndicatif() {
        assertThrows(IllegalArgumentException.class, () -> new TypeCellule(
                UUID.randomUUID(), UUID.randomUUID(), "Faculte", "Description", -1,
                null, null, StatutTypeCellule.ACTIF, TRACABILITE));
    }

    @Test
    void commandeAcceptsValidData() {
        assertDoesNotThrow(() -> new CreerTypeCelluleCommand(
                UUID.randomUUID(), "Faculte", "Description", 1, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
