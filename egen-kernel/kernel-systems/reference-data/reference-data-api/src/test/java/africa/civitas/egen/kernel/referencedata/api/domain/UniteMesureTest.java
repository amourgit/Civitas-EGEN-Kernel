package africa.civitas.egen.kernel.referencedata.api.domain;

import africa.civitas.egen.kernel.referencedata.api.command.EnregistrerUniteMesureCommand;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UniteMesureTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidUniteMesure() {
        assertDoesNotThrow(() -> new UniteMesure(
                UUID.randomUUID(), "kilometre", "km", CategorieUniteMesure.LONGUEUR,
                new BigDecimal("1000"), TRACABILITE));
    }

    @Test
    void rejectsAZeroFacteurConversion() {
        assertThrows(IllegalArgumentException.class, () -> new UniteMesure(
                UUID.randomUUID(), "kilometre", "km", CategorieUniteMesure.LONGUEUR,
                BigDecimal.ZERO, TRACABILITE));
    }

    @Test
    void rejectsANegativeFacteurConversion() {
        assertThrows(IllegalArgumentException.class, () -> new UniteMesure(
                UUID.randomUUID(), "kilometre", "km", CategorieUniteMesure.LONGUEUR,
                new BigDecimal("-1"), TRACABILITE));
    }

    @Test
    void commandeRejectsAMissingFacteurConversion() {
        assertThrows(NullPointerException.class, () -> new EnregistrerUniteMesureCommand(
                "kilometre", "km", CategorieUniteMesure.LONGUEUR, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }
}
