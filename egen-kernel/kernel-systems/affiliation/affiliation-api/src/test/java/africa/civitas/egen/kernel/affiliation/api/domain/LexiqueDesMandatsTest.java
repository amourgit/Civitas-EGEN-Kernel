package africa.civitas.egen.kernel.affiliation.api.domain;

import africa.civitas.egen.kernel.affiliation.api.command.CreerLexiqueDesMandatsCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexiqueDesMandatsTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidLexique() {
        LexiqueDesMandats l = new LexiqueDesMandats(
                UUID.randomUUID(), UUID.randomUUID(), "Lexique des Mandats", "Description", null, TRACABILITE);

        assertTrue(l.modeleSectorielOrigine().isEmpty());
    }

    @Test
    void rejectsABlankNom() {
        assertThrows(IllegalArgumentException.class, () -> new LexiqueDesMandats(
                UUID.randomUUID(), UUID.randomUUID(), " ", "Description", null, TRACABILITE));
    }

    @Test
    void commandeRejectsAMissingOrganisationId() {
        assertThrows(NullPointerException.class, () -> new CreerLexiqueDesMandatsCommand(
                null, "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
