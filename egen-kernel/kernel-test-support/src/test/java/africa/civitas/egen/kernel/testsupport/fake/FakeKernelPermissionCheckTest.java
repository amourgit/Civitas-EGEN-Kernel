package africa.civitas.egen.kernel.testsupport.fake;

import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeKernelPermissionCheckTest {

    private final FakeKernelPermissionCheck check = new FakeKernelPermissionCheck();

    @Test
    void theBootstrapSubjectIsAlwaysAuthorizedWithoutAnyExplicitGrant() {
        DecisionNoyau decision = check.verifier(KernelSubject.sujetBootstrap(), KernelCapability.CHARGER_MODULE);

        assertTrue(decision.autorise());
    }

    @Test
    void anOrdinarySubjectIsRefusedByDefault() {
        DecisionNoyau decision = check.verifier(KernelSubject.nouveau(), KernelCapability.CHARGER_MODULE);

        assertFalse(decision.autorise());
    }

    @Test
    void anExplicitlyAuthorizedSubjectIsAuthorizedForThatExactCapacity() {
        KernelSubject sujet = KernelSubject.nouveau();
        check.autoriser(sujet, KernelCapability.CHARGER_MODULE);

        DecisionNoyau decision = check.verifier(sujet, KernelCapability.CHARGER_MODULE);

        assertTrue(decision.autorise());
    }

    @Test
    void authorizationDoesNotLeakToADifferentCapacity() {
        KernelSubject sujet = KernelSubject.nouveau();
        check.autoriser(sujet, KernelCapability.CHARGER_MODULE);

        DecisionNoyau decision = check.verifier(sujet, KernelCapability.DECHARGER_MODULE);

        assertFalse(decision.autorise());
    }
}
