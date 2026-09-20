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
            "REGISTERED, DEPLOYING",
            "REGISTERED, FAILED",
            "DEPLOYING, RUNNING",
            "DEPLOYING, FAILED",
            "RUNNING, FAILED",
            "RUNNING, STOPPED",
            "FAILED, DEPLOYING",
            "STOPPED, DEPLOYING",
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
            "RUNNING, DECLARED",
            "STOPPED, RUNNING",
            "FAILED, RUNNING",
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
