package africa.civitas.egen.modules.business.organization.api.politique.domain;

import africa.civitas.egen.modules.business.organization.api.politique.command.CreerDerogationCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerogationTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidPermanentDerogation() {
        assertDoesNotThrow(() -> new Derogation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "8 caracteres",
                "Contrainte reglementaire sectorielle", LocalDate.of(2025, 1, 1), null, TRACABILITE));
    }

    @Test
    void rejectsABlankJustification() {
        assertThrows(IllegalArgumentException.class, () -> new Derogation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "8 caracteres", " ",
                LocalDate.of(2025, 1, 1), null, TRACABILITE));
    }

    @Test
    void rejectsADateFinBeforeDateEntreeVigueur() {
        assertThrows(IllegalArgumentException.class, () -> new Derogation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "8 caracteres", "Justification",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 1, 1), TRACABILITE));
    }

    @Test
    void estEnVigueurLeIsTrueWithinBounds() {
        Derogation d = new Derogation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "8 caracteres", "Justification",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), TRACABILITE);

        assertTrue(d.estEnVigueurLe(LocalDate.of(2025, 6, 15)));
        assertFalse(d.estEnVigueurLe(LocalDate.of(2026, 1, 1)));
        assertFalse(d.estEnVigueurLe(LocalDate.of(2024, 12, 31)));
    }

    @Test
    void estEnVigueurLeIsTrueIndefinitelyWithoutDateFin() {
        Derogation d = new Derogation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "8 caracteres", "Justification",
                LocalDate.of(2025, 1, 1), null, TRACABILITE);

        assertTrue(d.estEnVigueurLe(LocalDate.of(2099, 1, 1)));
    }

    @Test
    void commandeRejectsABlankValeur() {
        assertThrows(IllegalArgumentException.class, () -> new CreerDerogationCommand(
                UUID.randomUUID(), UUID.randomUUID(), " ", "Justification",
                LocalDate.of(2025, 1, 1), null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
