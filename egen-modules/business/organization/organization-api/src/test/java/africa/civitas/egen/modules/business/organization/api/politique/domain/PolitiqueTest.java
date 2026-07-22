package africa.civitas.egen.modules.business.organization.api.politique.domain;

import africa.civitas.egen.modules.business.organization.api.politique.command.CreerPolitiqueCommand;
import africa.civitas.egen.kernel.sdk.contexte.ContexteNature;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolitiqueTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidPolitiqueOnAnOrganisation() {
        assertDoesNotThrow(() -> new Politique(
                UUID.randomUUID(), UUID.randomUUID(), ContexteNature.ORGANISATION,
                DomainePolitique.SECURITE_MOT_DE_PASSE, "Longueur minimale", "12 caracteres",
                StatutPolitique.ACTIVE, LocalDate.of(2025, 1, 1), TRACABILITE));
    }

    @Test
    void buildsAValidPolitiqueOnACellule() {
        assertDoesNotThrow(() -> new Politique(
                UUID.randomUUID(), UUID.randomUUID(), ContexteNature.CELLULE,
                DomainePolitique.FORMAT_DATE, "Format europeen", "JJ/MM/AAAA",
                StatutPolitique.ACTIVE, LocalDate.of(2025, 1, 1), TRACABILITE));
    }

    @Test
    void rejectsABlankValeur() {
        assertThrows(IllegalArgumentException.class, () -> new Politique(
                UUID.randomUUID(), UUID.randomUUID(), ContexteNature.ORGANISATION,
                DomainePolitique.SECURITE_MOT_DE_PASSE, "Longueur minimale", " ",
                StatutPolitique.ACTIVE, LocalDate.of(2025, 1, 1), TRACABILITE));
    }

    @Test
    void commandeAcceptsValidData() {
        assertDoesNotThrow(() -> new CreerPolitiqueCommand(
                UUID.randomUUID(), ContexteNature.ORGANISATION, DomainePolitique.SECURITE_MOT_DE_PASSE,
                "Longueur minimale", "12 caracteres", LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
