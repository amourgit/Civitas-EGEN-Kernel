package africa.civitas.egen.kernel.organization.api.domain;

import africa.civitas.egen.kernel.organization.api.command.CreerTutelleCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutelleTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidTutelle() {
        Tutelle t = new Tutelle(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NatureTutelle.SANITAIRE,
                true, LocalDate.of(2025, 1, 1), null, null, TRACABILITE);

        assertTrue(t.dateFinEventuelle().isEmpty());
    }

    @Test
    void rejectsADateFinBeforeDateDebut() {
        assertThrows(IllegalArgumentException.class, () -> new Tutelle(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NatureTutelle.SANITAIRE,
                true, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 1, 1), null, TRACABILITE));
    }

    @Test
    void commandeAcceptsValidData() {
        assertDoesNotThrow(() -> new CreerTutelleCommand(
                UUID.randomUUID(), UUID.randomUUID(), NatureTutelle.ACADEMIQUE, false,
                LocalDate.of(2025, 1, 1), null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
