package africa.civitas.egen.api.mapper;

import africa.civitas.egen.api.dto.ExecutionPolicyDto;
import africa.civitas.egen.api.dto.MetadataDto;
import africa.civitas.egen.api.dto.StepCompensationDto;
import africa.civitas.egen.api.dto.StepRetryDto;
import africa.civitas.egen.api.dto.WorkflowDefinitionDto;
import africa.civitas.egen.api.dto.WorkflowExecutionDto;
import africa.civitas.egen.api.dto.WorkflowSpecDto;
import africa.civitas.egen.api.dto.WorkflowStepDto;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.RetryPolicy.BackoffStrategy;
import africa.civitas.egen.domain.workflow.Step;
import africa.civitas.egen.domain.workflow.StepId;
import africa.civitas.egen.domain.workflow.StepResult;
import africa.civitas.egen.domain.workflow.StepStatus;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowMapperTest {

    private static WorkflowStepDto step(String id, String service, String operation, String compensation,
                                        String... dependsOn) {
        WorkflowStepDto dto = new WorkflowStepDto();
        dto.id = id;
        dto.service = service;
        dto.operation = operation;
        dto.dependsOn = List.of(dependsOn);
        if (compensation != null) {
            dto.compensation = new StepCompensationDto();
            dto.compensation.operation = compensation;
        }
        return dto;
    }

    /** Le workflow de reference de docs/architecture/11, champ pour champ. */
    private static WorkflowDefinitionDto referenceDto() {
        WorkflowStepDto validate = step("validate-content", "news-service", "POST /internal/validate", null);
        validate.timeout = "10s";
        validate.retry = new StepRetryDto();
        validate.retry.maxAttempts = 3;
        validate.retry.backoff = "exponential";
        WorkflowDefinitionDto dto = new WorkflowDefinitionDto();
        dto.apiVersion = "egen.civitas.africa/v1";
        dto.kind = "WorkflowDefinition";
        dto.metadata = new MetadataDto();
        dto.metadata.name = "publish-article-workflow";
        dto.spec = new WorkflowSpecDto();
        dto.spec.steps = List.of(
                validate,
                step("reserve-notification-slot", "notification-service", "POST /internal/reserve-slot",
                        "POST /internal/release-slot", "validate-content"),
                step("publish", "news-service", "POST /internal/publish", "POST /internal/unpublish",
                        "reserve-notification-slot"),
                step("notify-subscribers", "notification-service", "EVENT africa.civitas.news.article.published.v1",
                        null, "publish"));
        dto.spec.compensationStrategy = "backward";
        dto.spec.executionPolicy = new ExecutionPolicyDto();
        dto.spec.executionPolicy.globalTimeout = "5m";
        dto.spec.executionPolicy.manualApprovalSteps = List.of();
        return dto;
    }

    @Test
    void theReferenceWorkflowOfDoc11MapsToTheDomain() {
        WorkflowDefinition definition = WorkflowMapper.toDomain(referenceDto());

        assertEquals(WorkflowId.of("publish-article-workflow"), definition.id());
        assertEquals(4, definition.steps().size());
        Step validate = definition.stepById(StepId.of("validate-content")).orElseThrow();
        assertEquals(Duration.ofSeconds(10), validate.timeout());
        assertEquals(3, validate.retry().maxAttempts());
        assertEquals(BackoffStrategy.EXPONENTIAL, validate.retry().backoff());
        assertEquals("POST /internal/release-slot", definition.stepById(StepId.of("reserve-notification-slot"))
                .orElseThrow().compensationOperation().orElseThrow().raw());
        assertEquals("EVENT africa.civitas.news.article.published.v1",
                definition.stepById(StepId.of("notify-subscribers")).orElseThrow().operation().raw());
        assertEquals(Duration.ofMinutes(5), definition.executionPolicy().globalTimeout());
        assertEquals(1, definition.executionPolicy().maxParallelSteps());
    }

    @Test
    void omittedStepFieldsTakeTheDocumentedDefaults() {
        Step publish = WorkflowMapper.toDomain(referenceDto()).stepById(StepId.of("publish")).orElseThrow();

        assertEquals(WorkflowMapper.DEFAULT_STEP_TIMEOUT, publish.timeout());
        assertEquals(1, publish.retry().maxAttempts());
    }

    @Test
    void toDtoThenToDomainIsALosslessRoundTrip() {
        WorkflowDefinition original = WorkflowMapper.toDomain(referenceDto());
        WorkflowDefinition roundTripped = WorkflowMapper.toDomain(WorkflowMapper.toDto(original));

        assertEquals(original.id(), roundTripped.id());
        assertEquals(original.topologicalOrder().size(), roundTripped.topologicalOrder().size());
        for (Step step : original.steps()) {
            assertEquals(step, roundTripped.stepById(step.id()).orElseThrow());
        }
        assertEquals(original.executionPolicy(), roundTripped.executionPolicy());
        assertEquals(original.compensationStrategy(), roundTripped.compensationStrategy());
    }

    @Test
    void toDtoListsStepsInTopologicalOrderWithReadableDurations() {
        WorkflowDefinitionDto dto = WorkflowMapper.toDto(WorkflowMapper.toDomain(referenceDto()));

        assertEquals("validate-content", dto.spec.steps.get(0).id);
        assertEquals("10s", dto.spec.steps.get(0).timeout);
        assertEquals("5m", dto.spec.executionPolicy.globalTimeout);
        assertEquals("backward", dto.spec.compensationStrategy);
        assertNull(dto.spec.steps.get(0).compensation);
        assertEquals("exponential", dto.spec.steps.get(0).retry.backoff);
    }

    @Test
    void durationsAcceptSecondsMinutesAndHoursOnly() {
        assertEquals(Duration.ofSeconds(10), WorkflowMapper.parseDuration("10s", "t"));
        assertEquals(Duration.ofMinutes(5), WorkflowMapper.parseDuration("5m", "t"));
        assertEquals(Duration.ofHours(2), WorkflowMapper.parseDuration("2h", "t"));
        for (String bad : new String[] {"10", "s", "1.5s", "-3s", "5d", "", "PT5S"}) {
            assertThrows(IllegalArgumentException.class, () -> WorkflowMapper.parseDuration(bad, "t"), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> WorkflowMapper.parseDuration(null, "t"));
    }

    @Test
    void formatIsReadableByParse() {
        for (Duration d : List.of(Duration.ofSeconds(45), Duration.ofSeconds(90), Duration.ofMinutes(5),
                Duration.ofHours(3), Duration.ofSeconds(3601))) {
            assertEquals(d, WorkflowMapper.parseDuration(WorkflowMapper.formatDuration(d), "t"));
        }
    }

    @Test
    void invalidContentIsRejectedWithAnExplicitMessage() {
        WorkflowDefinitionDto wrongKind = referenceDto();
        wrongKind.kind = "ServiceManifest";
        assertTrue(assertThrows(IllegalArgumentException.class, () -> WorkflowMapper.toDomain(wrongKind))
                .getMessage().contains("kind"));

        WorkflowDefinitionDto noName = referenceDto();
        noName.metadata.name = null;
        assertThrows(IllegalArgumentException.class, () -> WorkflowMapper.toDomain(noName));

        WorkflowDefinitionDto noSteps = referenceDto();
        noSteps.spec.steps = List.of();
        assertThrows(IllegalArgumentException.class, () -> WorkflowMapper.toDomain(noSteps));

        WorkflowDefinitionDto noTimeout = referenceDto();
        noTimeout.spec.executionPolicy.globalTimeout = null;
        assertTrue(assertThrows(IllegalArgumentException.class, () -> WorkflowMapper.toDomain(noTimeout))
                .getMessage().contains("globalTimeout"));

        WorkflowDefinitionDto badBackoff = referenceDto();
        badBackoff.spec.steps.get(0).retry.backoff = "quadratic";
        assertThrows(IllegalArgumentException.class, () -> WorkflowMapper.toDomain(badBackoff));

        WorkflowDefinitionDto badOperation = referenceDto();
        badOperation.spec.steps.get(1).operation = "FETCH nowhere";
        assertThrows(IllegalArgumentException.class, () -> WorkflowMapper.toDomain(badOperation));

        WorkflowDefinitionDto unknownDependency = referenceDto();
        unknownDependency.spec.steps.get(1).dependsOn = List.of("ghost");
        assertThrows(IllegalArgumentException.class, () -> WorkflowMapper.toDomain(unknownDependency));
    }

    @Test
    void aCyclicDependencyIsRejectedAtMappingTime() {
        WorkflowDefinitionDto cyclic = referenceDto();
        cyclic.spec.steps.get(0).dependsOn = List.of("notify-subscribers");

        assertThrows(IllegalArgumentException.class, () -> WorkflowMapper.toDomain(cyclic));
    }

    @Test
    void executionMapsEveryFieldOfEveryStepResult() {
        Instant t0 = Instant.parse("2026-10-09T10:00:00Z");
        ExecutionId id = ExecutionId.of("exec-1");
        WorkflowExecution execution = WorkflowExecution
                .start(WorkflowId.of("publish-article-workflow"), id, t0, Map.of("articleId", "42"))
                .withStepResult(new StepResult(StepId.of("a"), StepStatus.SUCCEEDED, 1, t0, t0.plusSeconds(1),
                        Map.of("k", "v"), null))
                .withStepResult(new StepResult(StepId.of("b"), StepStatus.FAILED, 2, t0, t0.plusSeconds(2),
                        Map.of(), "HTTP 500"))
                .withStepResult(new StepResult(StepId.of("c"), StepStatus.PENDING, 1, t0, null, Map.of(), null))
                .withStatus(WorkflowExecutionStatus.PAUSED);

        WorkflowExecutionDto dto = WorkflowMapper.toDto(execution);

        assertEquals("exec-1", dto.executionId);
        assertEquals("publish-article-workflow", dto.workflowId);
        assertEquals("PAUSED", dto.status);
        assertEquals("2026-10-09T10:00:00Z", dto.startedAt);
        assertEquals(Map.of("articleId", "42"), dto.input);
        assertEquals(3, dto.stepResults.size());
        assertEquals("SUCCEEDED", dto.stepResults.get(0).status);
        assertEquals(Map.of("k", "v"), dto.stepResults.get(0).output);
        assertEquals("2026-10-09T10:00:01Z", dto.stepResults.get(0).completedAt);
        assertEquals(2, dto.stepResults.get(1).attempt);
        assertEquals("HTTP 500", dto.stepResults.get(1).error);
        assertNull(dto.stepResults.get(2).completedAt);
        assertNull(dto.stepResults.get(2).error);
    }
}
