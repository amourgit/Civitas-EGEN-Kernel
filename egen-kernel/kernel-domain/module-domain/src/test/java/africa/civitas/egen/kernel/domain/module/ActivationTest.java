package africa.civitas.egen.kernel.domain.module;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivationTest {

    private static final Tracabilite UNE_TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void isActiveByDefault() {
        Activation activation = new Activation(
                UUID.randomUUID(), UUID.randomUUID(), new ModuleId("academie"), UNE_TRACABILITE);

        assertTrue(activation.active());
    }

    @Test
    void isNotActiveOnceLogicallyDeleted() {
        Tracabilite eteinte = UNE_TRACABILITE.avecSuppressionLogique(Acteur.systeme("test"), "desactivation");
        Activation activation = new Activation(
                UUID.randomUUID(), UUID.randomUUID(), new ModuleId("academie"), eteinte);

        assertFalse(activation.active());
    }

    @Test
    void rejectsANullCelluleId() {
        assertThrows(NullPointerException.class, () -> new Activation(
                UUID.randomUUID(), null, new ModuleId("academie"), UNE_TRACABILITE));
    }
}
