package africa.civitas.egen.kernel.identity.api.command;

import africa.civitas.egen.kernel.identity.api.domain.StatutVital;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreerPersonneCommandTest {

    private static final Acteur ACTEUR = Acteur.personne(UUID.randomUUID());

    @Test
    void acceptsAValidCommandWithoutIdentifiantCivilReference() {
        assertDoesNotThrow(() -> new CreerPersonneCommand(
                null, "Mba", null, List.of("Samuel"), LocalDate.of(1990, 1, 1),
                "Libreville", null, List.of("GA"), List.of("fr"), StatutVital.VIVANT,
                null, null, null, ACTEUR, OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    void rejectsAFutureDateNaissance() {
        assertThrows(IllegalArgumentException.class, () -> new CreerPersonneCommand(
                null, "Mba", null, List.of("Samuel"), LocalDate.now().plusYears(1),
                null, null, List.of("GA"), List.of(), StatutVital.VIVANT,
                null, null, null, ACTEUR, OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    void rejectsAMissingDemandeur() {
        assertThrows(NullPointerException.class, () -> new CreerPersonneCommand(
                null, "Mba", null, List.of("Samuel"), LocalDate.of(1990, 1, 1),
                null, null, List.of("GA"), List.of(), StatutVital.VIVANT,
                null, null, null, null, OrigineDonnee.SAISIE_MANUELLE));
    }
}
