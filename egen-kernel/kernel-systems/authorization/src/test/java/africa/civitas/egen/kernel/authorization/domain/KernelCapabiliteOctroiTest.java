package africa.civitas.egen.kernel.authorization.domain;

import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelCapabiliteOctroiTest {

    private static final Tracabilite UNE_TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void rejectsTheBootstrapIdAsBeneficiary() {
        assertThrows(IllegalArgumentException.class, () -> new KernelCapabiliteOctroi(
                UUID.randomUUID(), KernelSubject.BOOTSTRAP_ID,
                KernelCapability.CHARGER_MODULE, UNE_TRACABILITE));
    }

    @Test
    void acceptsAnOrdinarySubjectAsBeneficiary() {
        KernelCapabiliteOctroi octroi = new KernelCapabiliteOctroi(
                UUID.randomUUID(), UUID.randomUUID(), KernelCapability.CHARGER_MODULE, UNE_TRACABILITE);

        assertTrue(octroi.actif());
    }

    @Test
    void actifIsFalseOnceLogicallyDeleted() {
        Tracabilite revoquee = UNE_TRACABILITE.avecSuppressionLogique(Acteur.systeme("test"), "revoque");
        KernelCapabiliteOctroi octroi = new KernelCapabiliteOctroi(
                UUID.randomUUID(), UUID.randomUUID(), KernelCapability.CHARGER_MODULE, revoquee);

        assertFalse(octroi.actif());
    }
}
