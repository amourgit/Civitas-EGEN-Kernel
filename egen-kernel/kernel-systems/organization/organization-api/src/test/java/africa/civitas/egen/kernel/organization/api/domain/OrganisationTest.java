package africa.civitas.egen.kernel.organization.api.domain;

import africa.civitas.egen.kernel.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganisationTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidOrganisation() {
        assertDoesNotThrow(() -> new Organisation(
                UUID.randomUUID(), "Ministere de l'Education", "MINEDUC", TypeOrganisation.MINISTERE,
                "Education", "GA", "RCCM-0001", "XAF", List.of("fr"), "Africa/Libreville",
                null, StatutOrganisation.ACTIF, LocalDate.of(2025, 1, 1), null, TRACABILITE));
    }

    @Test
    void rejectsAFutureDateAdhesion() {
        assertThrows(IllegalArgumentException.class, () -> new Organisation(
                UUID.randomUUID(), "Ministere de l'Education", "MINEDUC", TypeOrganisation.MINISTERE,
                "Education", "GA", "RCCM-0001", "XAF", List.of("fr"), "Africa/Libreville",
                null, StatutOrganisation.ACTIF, LocalDate.now().plusDays(1), null, TRACABILITE));
    }

    @Test
    void rejectsAnEmptyLanguesOfficielles() {
        assertThrows(IllegalArgumentException.class, () -> new Organisation(
                UUID.randomUUID(), "Ministere de l'Education", "MINEDUC", TypeOrganisation.MINISTERE,
                "Education", "GA", "RCCM-0001", "XAF", List.of(), "Africa/Libreville",
                null, StatutOrganisation.ACTIF, LocalDate.of(2025, 1, 1), null, TRACABILITE));
    }

    @Test
    void modeleSectorielOrigineIsEmptyWhenNotProvided() {
        Organisation o = new Organisation(
                UUID.randomUUID(), "Orange Gabon", "ORANGE", TypeOrganisation.ENTREPRISE,
                "Telecommunications", "GA", "RCCM-0002", "XAF", List.of("fr"), "Africa/Libreville",
                null, StatutOrganisation.ACTIF, LocalDate.of(2025, 1, 1), null, TRACABILITE);

        assertTrue(o.modeleSectorielOrigine().isEmpty());
    }

    @Test
    void commandeRejectsABlankIdentifiantJuridique() {
        assertThrows(IllegalArgumentException.class, () -> new CreerOrganisationCommand(
                "Orange Gabon", "ORANGE", TypeOrganisation.ENTREPRISE, "Telecommunications",
                "GA", " ", "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
