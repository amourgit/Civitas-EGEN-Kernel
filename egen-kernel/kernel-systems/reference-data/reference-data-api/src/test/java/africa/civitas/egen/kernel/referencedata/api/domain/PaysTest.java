package africa.civitas.egen.kernel.referencedata.api.domain;

import africa.civitas.egen.kernel.referencedata.api.command.EnregistrerPaysCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaysTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);
    private static final Acteur ACTEUR = Acteur.systeme("test");

    @Test
    void buildsAValidPays() {
        assertDoesNotThrow(() -> new Pays(
                UUID.randomUUID(), "GA", "GAB", "Republique Gabonaise", "Gabon",
                "+241", "XAF", TRACABILITE));
    }

    @Test
    void rejectsALowercaseCodeAlpha2() {
        assertThrows(IllegalArgumentException.class, () -> new Pays(
                UUID.randomUUID(), "ga", "GAB", "Republique Gabonaise", "Gabon",
                "+241", "XAF", TRACABILITE));
    }

    @Test
    void rejectsAMalformedIndicatifTelephonique() {
        assertThrows(IllegalArgumentException.class, () -> new Pays(
                UUID.randomUUID(), "GA", "GAB", "Republique Gabonaise", "Gabon",
                "241", "XAF", TRACABILITE));
    }

    @Test
    void commandeRejectsAMalformedCodeAlpha3() {
        assertThrows(IllegalArgumentException.class, () -> new EnregistrerPaysCommand(
                "GA", "GA", "Republique Gabonaise", "Gabon", "+241", "XAF",
                ACTEUR, OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    void commandeAcceptsValidData() {
        assertDoesNotThrow(() -> new EnregistrerPaysCommand(
                "GA", "GAB", "Republique Gabonaise", "Gabon", "+241", "XAF",
                ACTEUR, OrigineDonnee.SAISIE_MANUELLE));
    }
}
