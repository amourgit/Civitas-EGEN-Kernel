package africa.civitas.egen.kernel.affiliation.api.domain;

import africa.civitas.egen.kernel.affiliation.api.command.CreerAffectationCommand;
import africa.civitas.egen.kernel.affiliation.api.command.TerminerAffectationCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AffectationTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidActiveAffectation() {
        assertDoesNotThrow(() -> new Affectation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                QuotiteEngagement.TEMPS_PLEIN, LocalDate.of(2025, 1, 1), null,
                StatutAffectation.ACTIVE, null, TRACABILITE));
    }

    @Test
    void rejectsATermineeStatusWithoutMotifFin() {
        assertThrows(IllegalArgumentException.class, () -> new Affectation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                QuotiteEngagement.TEMPS_PLEIN, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1),
                StatutAffectation.TERMINEE, null, TRACABILITE));
    }

    @Test
    void rejectsAMotifFinOnANonTermineeAffectation() {
        assertThrows(IllegalArgumentException.class, () -> new Affectation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                QuotiteEngagement.TEMPS_PLEIN, LocalDate.of(2025, 1, 1), null,
                StatutAffectation.ACTIVE, MotifFinAffectation.DEMISSION, TRACABILITE));
    }

    @Test
    void commandeCreerAcceptsValidData() {
        assertDoesNotThrow(() -> new CreerAffectationCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), QuotiteEngagement.TEMPS_PLEIN,
                LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    void commandeTerminerRejectsABlankMotif() {
        assertThrows(IllegalArgumentException.class, () -> new TerminerAffectationCommand(
                UUID.randomUUID(), LocalDate.of(2025, 6, 1), MotifFinAffectation.DEMISSION,
                Acteur.systeme("test"), " "));
    }
}
