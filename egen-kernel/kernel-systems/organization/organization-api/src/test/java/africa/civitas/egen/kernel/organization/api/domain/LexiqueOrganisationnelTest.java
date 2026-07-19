package africa.civitas.egen.kernel.organization.api.domain;

import africa.civitas.egen.kernel.organization.api.command.CreerLexiqueOrganisationnelCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexiqueOrganisationnelTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidLexique() {
        LexiqueOrganisationnel l = new LexiqueOrganisationnel(
                UUID.randomUUID(), UUID.randomUUID(), "Lexique Academique",
                "Vocabulaire de la hierarchie academique", null, TRACABILITE);

        assertTrue(l.modeleSectorielOrigine().isEmpty());
    }

    @Test
    void rejectsABlankNom() {
        assertThrows(IllegalArgumentException.class, () -> new LexiqueOrganisationnel(
                UUID.randomUUID(), UUID.randomUUID(), " ", "Description", null, TRACABILITE));
    }

    @Test
    void commandeRejectsAMissingOrganisationId() {
        assertThrows(NullPointerException.class, () -> new CreerLexiqueOrganisationnelCommand(
                null, "Lexique Academique", "Description", null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
