package africa.civitas.egen.modules.business.referencedata.api.domain;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerDeviseCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviseTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidDevise() {
        assertDoesNotThrow(() -> new Devise(UUID.randomUUID(), "XAF", "FCFA", "Franc CFA", 0, TRACABILITE));
    }

    @Test
    void rejectsANegativeNombreDecimales() {
        assertThrows(IllegalArgumentException.class,
                () -> new Devise(UUID.randomUUID(), "XAF", "FCFA", "Franc CFA", -1, TRACABILITE));
    }

    @Test
    void rejectsExcessiveNombreDecimales() {
        assertThrows(IllegalArgumentException.class,
                () -> new Devise(UUID.randomUUID(), "XAF", "FCFA", "Franc CFA", 5, TRACABILITE));
    }

    @Test
    void commandeRejectsAMalformedCode() {
        assertThrows(IllegalArgumentException.class, () -> new EnregistrerDeviseCommand(
                "xaf", "FCFA", "Franc CFA", 0, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
