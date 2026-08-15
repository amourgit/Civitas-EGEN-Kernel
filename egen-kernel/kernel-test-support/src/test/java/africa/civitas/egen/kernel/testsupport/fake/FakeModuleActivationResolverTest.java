package africa.civitas.egen.kernel.testsupport.fake;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeModuleActivationResolverTest {

    private final FakeModuleActivationResolver resolver = new FakeModuleActivationResolver();

    @Test
    void refusesByDefaultWhenNoActivationWasRegistered() {
        DecisionNoyau decision = resolver.estActifPour(UUID.randomUUID(), new ModuleId("academie"));

        assertFalse(decision.autorise());
    }

    @Test
    void authorizesOnceActivatedForThatExactContexteAndModule() {
        UUID contexteId = UUID.randomUUID();
        ModuleId moduleId = new ModuleId("academie");
        resolver.activerPour(contexteId, moduleId);

        DecisionNoyau decision = resolver.estActifPour(contexteId, moduleId);

        assertTrue(decision.autorise());
    }

    @Test
    void activationDoesNotLeakToADifferentContexte() {
        ModuleId moduleId = new ModuleId("academie");
        resolver.activerPour(UUID.randomUUID(), moduleId);

        DecisionNoyau decision = resolver.estActifPour(UUID.randomUUID(), moduleId);

        assertFalse(decision.autorise());
    }

    @Test
    void activationDoesNotLeakToADifferentModule() {
        UUID contexteId = UUID.randomUUID();
        resolver.activerPour(contexteId, new ModuleId("academie"));

        DecisionNoyau decision = resolver.estActifPour(contexteId, new ModuleId("rh"));

        assertFalse(decision.autorise());
    }
}
