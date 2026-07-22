package africa.civitas.egen.modules.system.identity.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonneTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidPersonne() {
        Personne p = new Personne(
                UUID.randomUUID(), "GA-0001", "Mba", null, List.of("Samuel"),
                LocalDate.of(1990, 1, 1), "Libreville", "Masculin",
                List.of("GA"), List.of("fr"), StatutVital.VIVANT,
                "+24101020304", null, null, StatutVerificationIdentite.NON_VERIFIEE, TRACABILITE);

        assertEquals("Samuel", p.prenoms().get(0));
        assertEquals("Mba", p.nomAffiche());
    }

    @Test
    void nomAfficheFallsBackToNomNaissanceWhenNoNomUsage() {
        Personne p = valid(null);
        assertEquals("Mba", p.nomAffiche());
    }

    @Test
    void nomAfficheUsesNomUsageWhenPresent() {
        Personne p = valid("Mba-Obame");
        assertEquals("Mba-Obame", p.nomAffiche());
    }

    @Test
    void rejectsAFutureDateNaissance() {
        assertThrows(IllegalArgumentException.class, () -> new Personne(
                UUID.randomUUID(), "GA-0001", "Mba", null, List.of("Samuel"),
                LocalDate.now().plusDays(1), null, null,
                List.of("GA"), List.of(), StatutVital.VIVANT,
                null, null, null, StatutVerificationIdentite.NON_VERIFIEE, TRACABILITE));
    }

    @Test
    void rejectsAnEmptyPrenomsList() {
        assertThrows(IllegalArgumentException.class, () -> new Personne(
                UUID.randomUUID(), "GA-0001", "Mba", null, List.of(),
                LocalDate.of(1990, 1, 1), null, null,
                List.of("GA"), List.of(), StatutVital.VIVANT,
                null, null, null, StatutVerificationIdentite.NON_VERIFIEE, TRACABILITE));
    }

    @Test
    void rejectsAnEmptyNationaliteList() {
        assertThrows(IllegalArgumentException.class, () -> new Personne(
                UUID.randomUUID(), "GA-0001", "Mba", null, List.of("Samuel"),
                LocalDate.of(1990, 1, 1), null, null,
                List.of(), List.of(), StatutVital.VIVANT,
                null, null, null, StatutVerificationIdentite.NON_VERIFIEE, TRACABILITE));
    }

    @Test
    void rejectsABlankIdentifiantCivilReference() {
        assertThrows(IllegalArgumentException.class, () -> new Personne(
                UUID.randomUUID(), "  ", "Mba", null, List.of("Samuel"),
                LocalDate.of(1990, 1, 1), null, null,
                List.of("GA"), List.of(), StatutVital.VIVANT,
                null, null, null, StatutVerificationIdentite.NON_VERIFIEE, TRACABILITE));
    }

    private static Personne valid(String nomUsage) {
        return new Personne(
                UUID.randomUUID(), "GA-0001", "Mba", nomUsage, List.of("Samuel"),
                LocalDate.of(1990, 1, 1), "Libreville", null,
                List.of("GA"), List.of("fr"), StatutVital.VIVANT,
                null, null, null, StatutVerificationIdentite.NON_VERIFIEE, TRACABILITE);
    }
}
