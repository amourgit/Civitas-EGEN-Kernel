package africa.civitas.egen.modules.business.referencedata.api.domain;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerFuseauHoraireCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FuseauHoraireTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidFuseauHoraire() {
        assertDoesNotThrow(() -> new FuseauHoraire(
                UUID.randomUUID(), "Africa/Libreville", "Afrique de l'Ouest", "+01:00", TRACABILITE));
    }

    @Test
    void rejectsAMalformedDecalage() {
        assertThrows(IllegalArgumentException.class, () -> new FuseauHoraire(
                UUID.randomUUID(), "Africa/Libreville", "Afrique de l'Ouest", "GMT+1", TRACABILITE));
    }

    @Test
    void commandeRejectsAMalformedDecalage() {
        assertThrows(IllegalArgumentException.class, () -> new EnregistrerFuseauHoraireCommand(
                "Africa/Libreville", "Afrique de l'Ouest", "+1:00",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
