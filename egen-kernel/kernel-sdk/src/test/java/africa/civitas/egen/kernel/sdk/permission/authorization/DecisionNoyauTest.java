package africa.civitas.egen.kernel.sdk.permission.authorization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionNoyauTest {

    @Test
    void autoriseFactoryBuildsAnAuthorizedDecisionWithItsMotif() {
        DecisionNoyau decision = DecisionNoyau.autorise("sujet bootstrap");

        assertTrue(decision.autorise());
        assertEquals("sujet bootstrap", decision.motif());
    }

    @Test
    void refuseFactoryBuildsARefusedDecisionWithItsMotif() {
        DecisionNoyau decision = DecisionNoyau.refuse("aucun octroi trouve");

        assertFalse(decision.autorise());
        assertEquals("aucun octroi trouve", decision.motif());
    }

    @Test
    void rejectsANullMotifEvenWhenAuthorized() {
        assertThrows(IllegalArgumentException.class, () -> new DecisionNoyau(true, null));
    }

    @Test
    void rejectsABlankMotifEvenWhenRefused() {
        assertThrows(IllegalArgumentException.class, () -> new DecisionNoyau(false, "   "));
    }
}
