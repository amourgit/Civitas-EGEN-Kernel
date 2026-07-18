package africa.civitas.egen.kernel.referencedata.api.domain;

import africa.civitas.egen.kernel.referencedata.api.command.CreerModeleSectorielCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModeleSectorielTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidModeleSectoriel() {
        assertDoesNotThrow(() -> new ModeleSectoriel(
                UUID.randomUUID(), "Education superieure", "Gabarit pour universites et grandes ecoles",
                "1.0.0", StatutModeleSectoriel.ACTIF, TRACABILITE));
    }

    @Test
    void rejectsABlankDescription() {
        assertThrows(IllegalArgumentException.class, () -> new ModeleSectoriel(
                UUID.randomUUID(), "Education superieure", " ", "1.0.0",
                StatutModeleSectoriel.ACTIF, TRACABILITE));
    }

    @Test
    void commandeRejectsABlankVersionModele() {
        assertThrows(IllegalArgumentException.class, () -> new CreerModeleSectorielCommand(
                "Education superieure", "Gabarit pour universites", " ",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
