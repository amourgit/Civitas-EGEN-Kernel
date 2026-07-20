package africa.civitas.egen.kernel.affiliation.api.domain;

import africa.civitas.egen.kernel.affiliation.api.command.CreerMandatCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MandatTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidMandat() {
        assertDoesNotThrow(() -> new Mandat(
                UUID.randomUUID(), UUID.randomUUID(), "Enseignant", "Dispense les cours", 1,
                null, null, StatutMandat.ACTIF, TRACABILITE));
    }

    @Test
    void rejectsSupervisingItself() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Mandat(
                id, UUID.randomUUID(), "Directeur", "Dirige", 5,
                List.of(id), null, StatutMandat.ACTIF, TRACABILITE));
    }

    @Test
    void rejectsANegativeNiveauAutorite() {
        assertThrows(IllegalArgumentException.class, () -> new Mandat(
                UUID.randomUUID(), UUID.randomUUID(), "Enseignant", "Dispense les cours", -1,
                null, null, StatutMandat.ACTIF, TRACABILITE));
    }

    @Test
    void commandeAcceptsValidData() {
        assertDoesNotThrow(() -> new CreerMandatCommand(
                UUID.randomUUID(), "Enseignant", "Dispense les cours", 1, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
