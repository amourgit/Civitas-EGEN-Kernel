package africa.civitas.egen.kernel.organization.api.domain;

import africa.civitas.egen.kernel.organization.api.command.CreerCelluleCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelluleTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void aRootCelluleIsAnEtablissement() {
        Cellule c = new Cellule(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "Universite Numerique", "UN-001", "Description", "GA", null,
                StatutCellule.ACTIF, LocalDate.of(2025, 1, 1), null, TRACABILITE);

        assertTrue(c.estEtablissement());
    }

    @Test
    void rejectsBeingItsOwnParent() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Cellule(
                id, UUID.randomUUID(), id, UUID.randomUUID(),
                "Faculte", "FAC-001", "Description", null, null,
                StatutCellule.ACTIF, LocalDate.of(2025, 1, 1), null, TRACABILITE));
    }

    @Test
    void rejectsAValidAuBeforeValidDu() {
        assertThrows(IllegalArgumentException.class, () -> new Cellule(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "Universite Numerique", "UN-001", "Description", null, null,
                StatutCellule.ACTIF, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 1, 1), TRACABILITE));
    }

    @Test
    void rejectsAFermeStatusWithoutValidAu() {
        assertThrows(IllegalArgumentException.class, () -> new Cellule(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "Universite Numerique", "UN-001", "Description", null, null,
                StatutCellule.FERME, LocalDate.of(2025, 1, 1), null, TRACABILITE));
    }

    @Test
    void commandeAcceptsARootCellule() {
        assertDoesNotThrow(() -> new CreerCelluleCommand(
                UUID.randomUUID(), null, UUID.randomUUID(), "Universite Numerique", "UN-001",
                "Description", null, null, LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
