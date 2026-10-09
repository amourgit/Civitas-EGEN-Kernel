package africa.civitas.egen.adapter.postgresregistry;

import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.workflow.CompensationStrategy;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.ExecutionPolicy;
import africa.civitas.egen.domain.workflow.Step;
import africa.civitas.egen.domain.workflow.StepId;
import africa.civitas.egen.domain.workflow.StepOperation;
import africa.civitas.egen.domain.workflow.StepResult;
import africa.civitas.egen.domain.workflow.StepStatus;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowId;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de niveau 3 (voir docs/architecture/17-strategie-de-tests.md) : demarre
 * un vrai PostgreSQL via Testcontainers et verifie le mapping de
 * {@link PostgresWorkflowStoreAdapter} contre l'API JDBC reelle — meme
 * conteneur partage que {@link PostgresRegistryAdapterIT}, meme discipline
 * d'isolation (chaque methode utilise son propre {@link WorkflowId}/
 * {@link ExecutionId}, voir la javadoc de cette classe-la pour le
 * raisonnement complet).
 */
@Testcontainers
class PostgresWorkflowStoreAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private PostgresWorkflowStoreAdapter adapter() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return new PostgresWorkflowStoreAdapter((DataSource) dataSource);
    }

    private static WorkflowDefinition aDefinition(WorkflowId id) {
        Step validate = new Step(StepId.of("validate-content"), ServiceId.of("news-service"),
                StepOperation.parse("POST /internal/validate"), List.of(), Duration.ofSeconds(10), null, null);
        Step publish = new Step(StepId.of("publish"), ServiceId.of("news-service"),
                StepOperation.parse("POST /internal/publish"), List.of(StepId.of("validate-content")),
                Duration.ofSeconds(10), null, StepOperation.parse("POST /internal/unpublish"));
        return WorkflowDefinition.of(id, List.of(validate, publish), CompensationStrategy.BACKWARD,
                ExecutionPolicy.sequential(Duration.ofMinutes(5)));
    }

    @Test
    void definitionRoundTripsThroughRealPostgresIncludingCompensationAndDependsOn() {
        WorkflowId id = WorkflowId.of("it-fixture-definition-round-trip");
        PostgresWorkflowStoreAdapter adapter = adapter();

        adapter.saveDefinition(aDefinition(id));
        WorkflowDefinition reloaded = adapter.findDefinition(id).orElseThrow();

        assertEquals(2, reloaded.steps().size());
        assertEquals(CompensationStrategy.BACKWARD, reloaded.compensationStrategy());
        Step publish = reloaded.stepById(StepId.of("publish")).orElseThrow();
        assertEquals(List.of(StepId.of("validate-content")), publish.dependsOn());
        assertEquals("POST /internal/unpublish", publish.compensationOperation().orElseThrow().raw());
    }

    @Test
    void anUnknownDefinitionIsEmpty() {
        PostgresWorkflowStoreAdapter adapter = adapter();
        assertTrue(adapter.findDefinition(WorkflowId.of("never-saved-workflow")).isEmpty());
    }

    @Test
    void executionRoundTripsWithItsStepResultsInInsertionOrder() {
        WorkflowId workflowId = WorkflowId.of("it-fixture-execution-round-trip");
        ExecutionId executionId = ExecutionId.generate();
        PostgresWorkflowStoreAdapter adapter = adapter();
        Instant t0 = Instant.now();

        WorkflowExecution execution = WorkflowExecution.start(workflowId, executionId, t0)
                .withStepResult(new StepResult(StepId.of("validate-content"), StepStatus.SUCCEEDED, 1, t0,
                        t0.plusSeconds(1), Map.of("valid", "true"), null))
                .withStepResult(new StepResult(StepId.of("publish"), StepStatus.SUCCEEDED, 1,
                        t0.plusSeconds(2), t0.plusSeconds(3), Map.of("publishedAt", "now"), null));
        adapter.save(execution);

        WorkflowExecution reloaded = adapter.findById(executionId).orElseThrow();
        assertEquals(WorkflowExecutionStatus.RUNNING, reloaded.status());
        assertEquals(List.of(StepId.of("validate-content"), StepId.of("publish")),
                reloaded.succeededStepsInExecutionOrder());
        assertEquals("true", reloaded.latestResultFor(StepId.of("validate-content")).orElseThrow()
                .output().get("valid"));
    }

    @Test
    void inputSurvivesTheRoundTripAndIsNeverOverwrittenByLaterSaves() {
        // Sans input persiste, une reprise apres redemarrage du Kernel
        // rejouerait les etapes avec un payload vide.
        WorkflowId workflowId = WorkflowId.of("it-fixture-input");
        ExecutionId executionId = ExecutionId.generate();
        PostgresWorkflowStoreAdapter adapter = adapter();
        Instant t0 = Instant.now();
        Map<String, String> input = Map.of("articleId", "42", "titre", "Libreville \"capitale\"");

        adapter.save(WorkflowExecution.start(workflowId, executionId, t0, input));
        // Une sauvegarde ulterieure construite SANS input ne doit pas l'ecraser.
        adapter.save(WorkflowExecution.start(workflowId, executionId, t0)
                .withStatus(WorkflowExecutionStatus.COMPLETED));

        WorkflowExecution reloaded = adapter.findById(executionId).orElseThrow();
        assertEquals(input, reloaded.input());
        assertEquals(WorkflowExecutionStatus.COMPLETED, reloaded.status());
    }

    @Test
    void savingTwiceWithTheSameStepResultsNeverDuplicatesThem() {
        WorkflowId workflowId = WorkflowId.of("it-fixture-idempotent-save");
        ExecutionId executionId = ExecutionId.generate();
        PostgresWorkflowStoreAdapter adapter = adapter();
        Instant t0 = Instant.now();

        WorkflowExecution execution = WorkflowExecution.start(workflowId, executionId, t0)
                .withStepResult(new StepResult(StepId.of("validate-content"), StepStatus.SUCCEEDED, 1, t0,
                        t0.plusSeconds(1), Map.of(), null));

        adapter.save(execution);
        adapter.save(execution); // meme contenu rejoue — doit rester idempotent

        WorkflowExecution reloaded = adapter.findById(executionId).orElseThrow();
        assertEquals(1, reloaded.stepResults().size());
    }

    @Test
    void savingAgainAfterAStatusTransitionPersistsTheNewStatusWithoutLosingPriorStepResults() {
        WorkflowId workflowId = WorkflowId.of("it-fixture-status-transition");
        ExecutionId executionId = ExecutionId.generate();
        PostgresWorkflowStoreAdapter adapter = adapter();
        Instant t0 = Instant.now();

        WorkflowExecution execution = WorkflowExecution.start(workflowId, executionId, t0)
                .withStepResult(new StepResult(StepId.of("validate-content"), StepStatus.FAILED, 1, t0,
                        t0.plusSeconds(1), Map.of(), "service unavailable"));
        adapter.save(execution);

        WorkflowExecution compensating = execution.withStatus(WorkflowExecutionStatus.COMPENSATING);
        adapter.save(compensating);

        WorkflowExecution reloaded = adapter.findById(executionId).orElseThrow();
        assertEquals(WorkflowExecutionStatus.COMPENSATING, reloaded.status());
        assertEquals(1, reloaded.stepResults().size());
    }

    @Test
    void findRunningExecutionsExcludesPausedCompletedAndOtherWorkflows() {
        PostgresWorkflowStoreAdapter adapter = adapter();
        WorkflowId workflowId = WorkflowId.of("it-fixture-find-running");
        Instant t0 = Instant.now();

        ExecutionId running = ExecutionId.generate();
        ExecutionId compensating = ExecutionId.generate();
        ExecutionId paused = ExecutionId.generate();
        ExecutionId completed = ExecutionId.generate();

        adapter.save(WorkflowExecution.start(workflowId, running, t0));
        adapter.save(WorkflowExecution.start(workflowId, compensating, t0)
                .withStatus(WorkflowExecutionStatus.COMPENSATING));
        adapter.save(WorkflowExecution.start(workflowId, paused, t0)
                .withStatus(WorkflowExecutionStatus.PAUSED));
        adapter.save(WorkflowExecution.start(workflowId, completed, t0)
                .withStatus(WorkflowExecutionStatus.COMPLETED));

        List<ExecutionId> runningExecutions = adapter.findRunningExecutions();

        assertTrue(runningExecutions.contains(running));
        assertTrue(runningExecutions.contains(compensating));
        assertFalse(runningExecutions.contains(paused));
        assertFalse(runningExecutions.contains(completed));
    }

    @Test
    void anUnknownExecutionIsEmpty() {
        PostgresWorkflowStoreAdapter adapter = adapter();
        assertTrue(adapter.findById(ExecutionId.of("never-saved-execution")).isEmpty());
    }
}
