package africa.civitas.egen.kernel.authorization.application;

import africa.civitas.egen.kernel.authorization.command.AccorderCapaciteCommand;
import africa.civitas.egen.kernel.authorization.command.RevoquerCapaciteCommand;
import africa.civitas.egen.kernel.authorization.domain.KernelCapabiliteOctroi;
import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class KernelPermissionCheckImplTest {

    @Inject
    KernelPermissionCheckImpl permissionCheck;

    @Inject
    KernelCapabiliteOctroiServiceImpl octroiService;

    @Test
    @TestTransaction
    void theBootstrapSubjectIsAlwaysAuthorizedForEveryCapacity() {
        for (KernelCapability capacite : KernelCapability.values()) {
            DecisionNoyau decision = permissionCheck.verifier(KernelSubject.sujetBootstrap(), capacite);
            assertTrue(decision.autorise(), "attendu autorise pour " + capacite);
        }
    }

    @Test
    @TestTransaction
    void anOrdinarySubjectWithoutAnyGrantIsDeniedByDefault() {
        DecisionNoyau decision = permissionCheck.verifier(KernelSubject.nouveau(), KernelCapability.CHARGER_MODULE);

        assertFalse(decision.autorise());
    }

    @Test
    @TestTransaction
    void anOrdinarySubjectWithAnActiveGrantIsAuthorized() {
        KernelSubject sujet = KernelSubject.nouveau();
        octroiService.accorder(new AccorderCapaciteCommand(
                sujet.id(), KernelCapability.CHARGER_MODULE, KernelSubject.sujetBootstrap(), OrigineDonnee.SAISIE_MANUELLE));

        DecisionNoyau decision = permissionCheck.verifier(sujet, KernelCapability.CHARGER_MODULE);

        assertTrue(decision.autorise());
    }

    @Test
    @TestTransaction
    void aGrantOnlyAuthorizesTheSpecificCapacityItWasGrantedFor() {
        KernelSubject sujet = KernelSubject.nouveau();
        octroiService.accorder(new AccorderCapaciteCommand(
                sujet.id(), KernelCapability.CHARGER_MODULE, KernelSubject.sujetBootstrap(), OrigineDonnee.SAISIE_MANUELLE));

        DecisionNoyau decision = permissionCheck.verifier(sujet, KernelCapability.DECHARGER_MODULE);

        assertFalse(decision.autorise());
    }

    @Test
    @TestTransaction
    void aRevokedGrantNoLongerAuthorizesTheSubject() {
        KernelSubject sujet = KernelSubject.nouveau();
        KernelCapabiliteOctroi octroi = octroiService.accorder(new AccorderCapaciteCommand(
                sujet.id(), KernelCapability.CHARGER_MODULE, KernelSubject.sujetBootstrap(), OrigineDonnee.SAISIE_MANUELLE));
        octroiService.revoquer(new RevoquerCapaciteCommand(octroi.id(), KernelSubject.sujetBootstrap(), "fin de mission"));

        DecisionNoyau decision = permissionCheck.verifier(sujet, KernelCapability.CHARGER_MODULE);

        assertFalse(decision.autorise());
    }
}
