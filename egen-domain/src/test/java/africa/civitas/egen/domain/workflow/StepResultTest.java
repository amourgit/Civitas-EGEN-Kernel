package africa.civitas.egen.domain.workflow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepResultTest {

    private static final StepId STEP_ID = StepId.of("validate-content");
    private static final Instant NOW = Instant.now();

    @Test
    void aRunningResultHasNoCompletedAtAndNoError() {
        StepResult result = new StepResult(STEP_ID, StepStatus.RUNNING, 1, NOW, null, Map.of(), null);
        assertTrue(result.completion().isEmpty());
        assertTrue(result.error().isEmpty());
        assertFalse(result.isTerminal());
    }

    @Test
    void aSucceededResultRequiresCompletedAt() {
        assertThrows(IllegalArgumentException.class,
                () -> new StepResult(STEP_ID, StepStatus.SUCCEEDED, 1, NOW, null, Map.of(), null));
    }

    @Test
    void aFailedResultRequiresAnErrorMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> new StepResult(STEP_ID, StepStatus.FAILED, 1, NOW, NOW.plusSeconds(1), Map.of(), null));
    }

    @Test
    void aSucceededResultMustNotCarryAnErrorMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> new StepResult(STEP_ID, StepStatus.SUCCEEDED, 1, NOW, NOW.plusSeconds(1), Map.of(), "huh?"));
    }

    @Test
    void aNonTerminalResultMustNotCarryACompletedAt() {
        assertThrows(IllegalArgumentException.class,
                () -> new StepResult(STEP_ID, StepStatus.RUNNING, 1, NOW, NOW.plusSeconds(1), Map.of(), null));
    }

    @Test
    void attemptMustBeAtLeastOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new StepResult(STEP_ID, StepStatus.RUNNING, 0, NOW, null, Map.of(), null));
    }

    @Test
    void compensationFailedIsTerminalAndRequiresAnError() {
        StepResult result = new StepResult(STEP_ID, StepStatus.COMPENSATION_FAILED, 1, NOW, NOW.plusSeconds(1),
                Map.of(), "release-slot failed");
        assertTrue(result.isTerminal());
    }
}
