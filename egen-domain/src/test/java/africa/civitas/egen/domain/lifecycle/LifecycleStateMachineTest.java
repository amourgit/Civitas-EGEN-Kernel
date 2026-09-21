package africa.civitas.egen.domain.lifecycle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleStateMachineTest {

    private final LifecycleStateMachine machine = new LifecycleStateMachine();

    @ParameterizedTest
    @CsvSource({
            "DECLARED, REGISTERED",
            "REGISTERED, CONFIGURED",
            "REGISTERED, FAILED",
            "CONFIGURED, DEPLOYING",
            "CONFIGURED, FAILED",
            "DEPLOYING, RUNNING",
            "DEPLOYING, FAILED",
            "RUNNING, DEGRADED",
            "RUNNING, UPDATING",
            "RUNNING, STOPPING",
            "RUNNING, FAILED",
            "DEGRADED, RUNNING",
            "DEGRADED, STOPPING",
            "DEGRADED, FAILED",
            "FAILED, DEPLOYING",
            "FAILED, STOPPING",
            "UPDATING, RUNNING",
            "UPDATING, ROLLED_BACK",
            "UPDATING, FAILED",
            "ROLLED_BACK, RUNNING",
            "STOPPING, STOPPED",
            "STOPPED, DEPLOYING",
            "STOPPED, REMOVING",
            "REMOVING, REMOVED",
    })
    void allowsDocumentedTransitions(Phase from, Phase to) {
        assertTrue(machine.isTransitionAllowed(from, to));
        assertDoesNotThrow(() -> machine.assertTransitionAllowed(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "DECLARED, RUNNING",
            "DECLARED, DEPLOYING",
            "REGISTERED, RUNNING",
            "RUNNING, STOPPED",       // doit passer par STOPPING (arret gracieux, voir 09.3)
            "RUNNING, DECLARED",
            "STOPPED, RUNNING",
            "FAILED, RUNNING",
            "REMOVED, DEPLOYING",     // REMOVED est terminal
    })
    void rejectsUndocumentedTransitions(Phase from, Phase to) {
        assertFalse(machine.isTransitionAllowed(from, to));
        assertThrows(IllegalPhaseTransitionException.class,
                () -> machine.assertTransitionAllowed(from, to));
    }

    @Test
    void stayingInTheSamePhaseIsAlwaysAllowed() {
        for (Phase phase : Phase.values()) {
            assertTrue(machine.isTransitionAllowed(phase, phase));
        }
    }
}
