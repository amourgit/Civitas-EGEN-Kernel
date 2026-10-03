package africa.civitas.egen.domain.workflow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowExecutionTest {

    private static final WorkflowId WORKFLOW_ID = WorkflowId.of("publish-article-workflow");
    private static final ExecutionId EXECUTION_ID = ExecutionId.generate();
    private static final StepId STEP_A = StepId.of("validate-content");
    private static final StepId STEP_B = StepId.of("publish");

    private static StepResult succeeded(StepId stepId, Instant at) {
        return new StepResult(stepId, StepStatus.SUCCEEDED, 1, at, at.plusSeconds(1), java.util.Map.of(), null);
    }

    private static StepResult failed(StepId stepId, Instant at) {
        return new StepResult(stepId, StepStatus.FAILED, 1, at, at.plusSeconds(1), java.util.Map.of(), "boom");
    }

    @Test
    void startsInRunningWithNoStepResults() {
        WorkflowExecution execution = WorkflowExecution.start(WORKFLOW_ID, EXECUTION_ID, Instant.now());

        assertEquals(WorkflowExecutionStatus.RUNNING, execution.status());
        assertTrue(execution.stepResults().isEmpty());
    }

    @Test
    void withStepResultAppendsRatherThanReplaces() {
        Instant t0 = Instant.now();
        WorkflowExecution execution = WorkflowExecution.start(WORKFLOW_ID, EXECUTION_ID, t0)
                .withStepResult(new StepResult(STEP_A, StepStatus.RUNNING, 1, t0, null, java.util.Map.of(), null))
                .withStepResult(succeeded(STEP_A, t0.plusSeconds(1)));

        // Deux entrees distinctes pour la MEME etape (RUNNING puis SUCCEEDED)
        // — jamais une entree remplacee par une autre (discipline append-only).
        assertEquals(2, execution.stepResults().size());
        // latestResultFor() refere bien la DERNIERE entree.
        assertEquals(StepStatus.SUCCEEDED, execution.latestResultFor(STEP_A).orElseThrow().status());
        assertTrue(execution.hasSucceeded(STEP_A));
    }

    @Test
    void succeededStepsInExecutionOrderReflectsAppendOrderWithoutDuplicates() {
        Instant t0 = Instant.now();
        WorkflowExecution execution = WorkflowExecution.start(WORKFLOW_ID, EXECUTION_ID, t0)
                .withStepResult(succeeded(STEP_A, t0))
                .withStepResult(succeeded(STEP_B, t0.plusSeconds(1)));

        assertEquals(List.of(STEP_A, STEP_B), execution.succeededStepsInExecutionOrder());
    }

    @Test
    void aFailedStepIsNeverCountedAsSucceeded() {
        WorkflowExecution execution = WorkflowExecution.start(WORKFLOW_ID, EXECUTION_ID, Instant.now())
                .withStepResult(failed(STEP_A, Instant.now()));

        assertFalse(execution.hasSucceeded(STEP_A));
        assertTrue(execution.succeededStepsInExecutionOrder().isEmpty());
    }

    @Test
    void allowsRunningToCompensatingToCompensated() {
        WorkflowExecution execution = WorkflowExecution.start(WORKFLOW_ID, EXECUTION_ID, Instant.now())
                .withStatus(WorkflowExecutionStatus.COMPENSATING)
                .withStatus(WorkflowExecutionStatus.COMPENSATED);

        assertEquals(WorkflowExecutionStatus.COMPENSATED, execution.status());
    }

    @Test
    void rejectsATerminalStatusComingBackToLife() {
        WorkflowExecution completed = WorkflowExecution.start(WORKFLOW_ID, EXECUTION_ID, Instant.now())
                .withStatus(WorkflowExecutionStatus.COMPLETED);

        assertThrows(IllegalExecutionTransitionException.class,
                () -> completed.withStatus(WorkflowExecutionStatus.RUNNING));
    }

    @Test
    void rejectsCompensationFailedGoingAnywhereElse() {
        WorkflowExecution compensationFailed = WorkflowExecution.start(WORKFLOW_ID, EXECUTION_ID, Instant.now())
                .withStatus(WorkflowExecutionStatus.COMPENSATING)
                .withStatus(WorkflowExecutionStatus.COMPENSATION_FAILED);

        assertThrows(IllegalExecutionTransitionException.class,
                () -> compensationFailed.withStatus(WorkflowExecutionStatus.COMPENSATING));
    }

    @Test
    void pauseAndResumeRoundTripsBackToRunning() {
        WorkflowExecution execution = WorkflowExecution.start(WORKFLOW_ID, EXECUTION_ID, Instant.now())
                .withStatus(WorkflowExecutionStatus.PAUSED)
                .withStatus(WorkflowExecutionStatus.RUNNING);

        assertEquals(WorkflowExecutionStatus.RUNNING, execution.status());
    }

    @Test
    void stayingInTheSameStatusIsAlwaysAllowed() {
        WorkflowExecution execution = WorkflowExecution.start(WORKFLOW_ID, EXECUTION_ID, Instant.now());
        assertEquals(WorkflowExecutionStatus.RUNNING,
                execution.withStatus(WorkflowExecutionStatus.RUNNING).status());
    }
}
