package africa.civitas.egen.kernel.domain.module;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SouscriptionTest {

    private static final Tracabilite UNE_TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void isActiveByDefault() {
        Souscription souscription = new Souscription(
                UUID.randomUUID(), UUID.randomUUID(), new ModuleId("academie"), UNE_TRACABILITE);

        assertTrue(souscription.active());
    }

    @Test
    void isNotActiveOnceLogicallyDeleted() {
        Tracabilite resiliee = UNE_TRACABILITE.avecSuppressionLogique(Acteur.systeme("test"), "resiliation");
        Souscription souscription = new Souscription(
                UUID.randomUUID(), UUID.randomUUID(), new ModuleId("academie"), resiliee);

        assertFalse(souscription.active());
    }

    @Test
    void rejectsANullContexteId() {
        assertThrows(NullPointerException.class, () -> new Souscription(
                UUID.randomUUID(), null, new ModuleId("academie"), UNE_TRACABILITE));
    }
}
