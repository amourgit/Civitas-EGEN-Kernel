package africa.civitas.egen.kernel.organization.api.domain;

import africa.civitas.egen.kernel.organization.api.command.CreerSuccessionOrganisationnelleCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SuccessionOrganisationnelleTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidFusion() {
        assertDoesNotThrow(() -> new SuccessionOrganisationnelle(
                UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()),
                List.of(UUID.randomUUID()), NatureSuccession.FUSION,
                LocalDate.of(2025, 1, 1), null, TRACABILITE));
    }

    @Test
    void rejectsAnEmptyCelluleOrigineList() {
        assertThrows(IllegalArgumentException.class, () -> new SuccessionOrganisationnelle(
                UUID.randomUUID(), List.of(), List.of(UUID.randomUUID()),
                NatureSuccession.FUSION, LocalDate.of(2025, 1, 1), null, TRACABILITE));
    }

    @Test
    void rejectsAnEmptyCelluleHeritiereList() {
        assertThrows(IllegalArgumentException.class, () -> new SuccessionOrganisationnelle(
                UUID.randomUUID(), List.of(UUID.randomUUID()), List.of(),
                NatureSuccession.FUSION, LocalDate.of(2025, 1, 1), null, TRACABILITE));
    }

    @Test
    void commandeRejectsAnEmptyCelluleOrigineList() {
        assertThrows(IllegalArgumentException.class, () -> new CreerSuccessionOrganisationnelleCommand(
                List.of(), List.of(UUID.randomUUID()), NatureSuccession.SCISSION,
                LocalDate.of(2025, 1, 1), null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
