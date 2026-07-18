package africa.civitas.egen.kernel.referencedata.api.domain;

import africa.civitas.egen.kernel.referencedata.api.command.AjouterMandatModeleCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MandatModeleTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidMandatModele() {
        assertDoesNotThrow(() -> new MandatModele(
                UUID.randomUUID(), UUID.randomUUID(), "Enseignant", 1,
                "Dispense les cours et evalue les etudiants", TRACABILITE));
    }

    @Test
    void rejectsABlankDescriptionResponsabilites() {
        assertThrows(IllegalArgumentException.class, () -> new MandatModele(
                UUID.randomUUID(), UUID.randomUUID(), "Enseignant", 1, " ", TRACABILITE));
    }

    @Test
    void commandeRejectsANegativeNiveauAutorite() {
        assertThrows(IllegalArgumentException.class, () -> new AjouterMandatModeleCommand(
                UUID.randomUUID(), "Enseignant", -1, "Description",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
