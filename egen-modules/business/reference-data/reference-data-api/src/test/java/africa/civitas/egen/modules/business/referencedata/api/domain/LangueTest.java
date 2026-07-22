package africa.civitas.egen.modules.business.referencedata.api.domain;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerLangueCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LangueTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidLangue() {
        assertDoesNotThrow(() -> new Langue(
                UUID.randomUUID(), "fr", "Francais", "Francais", SensEcriture.GAUCHE_A_DROITE, TRACABILITE));
    }

    @Test
    void rejectsAnUppercaseCode() {
        assertThrows(IllegalArgumentException.class, () -> new Langue(
                UUID.randomUUID(), "FR", "Francais", "Francais", SensEcriture.GAUCHE_A_DROITE, TRACABILITE));
    }

    @Test
    void commandeRejectsAMissingSensEcriture() {
        assertThrows(NullPointerException.class, () -> new EnregistrerLangueCommand(
                "fr", "Francais", "Francais", null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
