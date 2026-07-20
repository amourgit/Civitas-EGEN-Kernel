package africa.civitas.egen.kernel.affiliation.api.domain;

import africa.civitas.egen.kernel.affiliation.api.command.CreerDelegationCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DelegationTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidTotaleDelegation() {
        assertDoesNotThrow(() -> new Delegation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EtendueDelegation.TOTALE, null,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 15), MotifDelegation.CONGE,
                StatutDelegation.PROGRAMMEE, TRACABILITE));
    }

    @Test
    void rejectsAPartielleDelegationWithoutActionsCouvertes() {
        assertThrows(IllegalArgumentException.class, () -> new Delegation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EtendueDelegation.PARTIELLE, null,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 15), MotifDelegation.CONGE,
                StatutDelegation.PROGRAMMEE, TRACABILITE));
    }

    @Test
    void rejectsATotaleDelegationWithActionsCouvertes() {
        assertThrows(IllegalArgumentException.class, () -> new Delegation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EtendueDelegation.TOTALE,
                "Signature des bulletins uniquement",
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 15), MotifDelegation.CONGE,
                StatutDelegation.PROGRAMMEE, TRACABILITE));
    }

    @Test
    void rejectsANullDateFin() {
        assertThrows(NullPointerException.class, () -> new Delegation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EtendueDelegation.TOTALE, null,
                LocalDate.of(2025, 7, 1), null, MotifDelegation.CONGE,
                StatutDelegation.PROGRAMMEE, TRACABILITE));
    }

    @Test
    void commandeRejectsAPartielleWithoutActionsCouvertes() {
        assertThrows(IllegalArgumentException.class, () -> new CreerDelegationCommand(
                UUID.randomUUID(), UUID.randomUUID(), EtendueDelegation.PARTIELLE, null,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 15), MotifDelegation.VOYAGE,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
