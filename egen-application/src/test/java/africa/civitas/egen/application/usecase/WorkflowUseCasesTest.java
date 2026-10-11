package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.workflow.ExecutionNotFoundException;
import africa.civitas.egen.application.workflow.InMemoryWorkflowStore;
import africa.civitas.egen.application.workflow.WorkflowEnginePort;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.workflow.CompensationStrategy;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.ExecutionPolicy;
import africa.civitas.egen.domain.workflow.RetryPolicy;
import africa.civitas.egen.domain.workflow.Step;
import africa.civitas.egen.domain.workflow.StepId;
import africa.civitas.egen.domain.workflow.StepOperation;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowId;
import africa.civitas.egen.domain.workflow.WorkflowInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowUseCasesTest {

    private static final WorkflowId WORKFLOW_ID = WorkflowId.of("publish-article-workflow");

    private static final class RecordingEngine implements WorkflowEnginePort {
        final List<String> calls = new ArrayList<>();
        WorkflowDefinition startedDefinition;
        WorkflowInput startedInput;
        final ExecutionId returned = ExecutionId.generate();

        @Override
        public ExecutionId start(WorkflowDefinition definition, WorkflowInput input) {
            startedDefinition = definition;
            startedInput = input;
            return returned;
        }

        @Override
        public void pause(ExecutionId id) {
            calls.add("pause " + id);
        }

        @Override
        public void resume(ExecutionId id) {
            calls.add("resume " + id);
        }

        @Override
        public void terminate(ExecutionId id, String reason) {
            calls.add("terminate " + id + " : " + reason);
        }

        @Override
        public WorkflowExecutionStatus status(ExecutionId id) {
            throw new UnsupportedOperationException();
        }
    }

    private InMemoryWorkflowStore store;
    private RecordingEngine engine;
    private DefineWorkflowUseCase define;
    private RunWorkflowUseCase run;

    private static WorkflowDefinition definition() {
        Step only = new Step(StepId.of("validate"), ServiceId.of("news-service"),
                StepOperation.parse("POST /internal/validate"), List.of(), Duration.ofSeconds(5),
                RetryPolicy.none(), null);
        return WorkflowDefinition.of(WORKFLOW_ID, List.of(only), CompensationStrategy.BACKWARD,
                ExecutionPolicy.sequential(Duration.ofMinutes(1)));
    }

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        engine = new RecordingEngine();
        define = new DefineWorkflowUseCaseImpl(store);
        run = new RunWorkflowUseCaseImpl(store, engine);
    }

    @Test
    void definePersistsTheDefinitionAndGetReadsItBack() {
        WorkflowDefinition definition = definition();
        define.define(definition);

        assertSame(definition, define.get(WORKFLOW_ID));
    }

    @Test
    void defineTwiceReplacesTheDefinition() {
        define.define(definition());
        WorkflowDefinition second = definition();
        define.define(second);

        assertSame(second, define.get(WORKFLOW_ID));
    }

    @Test
    void getOfAnUnknownDefinitionIsReportedExplicitly() {
        assertThrows(WorkflowNotFoundException.class, () -> define.get(WorkflowId.of("inconnu")));
    }

    @Test
    void runStartsTheStoredDefinitionWithTheInputAndReturnsTheExecutionId() {
        define.define(definition());
        WorkflowInput input = new WorkflowInput(Map.of("articleId", "42"));

        ExecutionId id = run.run(WORKFLOW_ID, input);

        assertEquals(engine.returned, id);
        assertSame(store.findDefinition(WORKFLOW_ID).orElseThrow(), engine.startedDefinition);
        assertEquals(input, engine.startedInput);
    }

    @Test
    void runWithoutInputStartsWithAnEmptyInput() {
        define.define(definition());
        run.run(WORKFLOW_ID, null);

        assertEquals(WorkflowInput.empty(), engine.startedInput);
    }

    @Test
    void runOfAnUnknownWorkflowNeverReachesTheEngine() {
        assertThrows(WorkflowNotFoundException.class, () -> run.run(WORKFLOW_ID, WorkflowInput.empty()));
        assertTrue(engine.startedDefinition == null);
    }

    @Test
    void observeReturnsThePersistedExecutionOrFailsExplicitly() {
        ExecutionId id = ExecutionId.generate();
        store.save(WorkflowExecution.start(WORKFLOW_ID, id, Instant.parse("2026-10-09T10:00:00Z")));

        assertEquals(WorkflowExecutionStatus.RUNNING, run.observe(id).status());
        assertThrows(ExecutionNotFoundException.class, () -> run.observe(ExecutionId.generate()));
    }

    @Test
    void pauseResumeAndTerminateAreDelegatedToTheEngine() {
        ExecutionId id = ExecutionId.generate();
        run.pause(id);
        run.resume(id);
        run.terminate(id, "annule");
        run.terminate(id, "  ");

        assertEquals(List.of("pause " + id, "resume " + id, "terminate " + id + " : annule",
                "terminate " + id + " : arret demande via l'API"), engine.calls);
    }
}
