package africa.civitas.egen.api.mapper;

import africa.civitas.egen.api.dto.ExecutionPolicyDto;
import africa.civitas.egen.api.dto.MetadataDto;
import africa.civitas.egen.api.dto.StepCompensationDto;
import africa.civitas.egen.api.dto.StepResultDto;
import africa.civitas.egen.api.dto.StepRetryDto;
import africa.civitas.egen.api.dto.WorkflowDefinitionDto;
import africa.civitas.egen.api.dto.WorkflowExecutionDto;
import africa.civitas.egen.api.dto.WorkflowSpecDto;
import africa.civitas.egen.api.dto.WorkflowStepDto;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.workflow.CompensationStrategy;
import africa.civitas.egen.domain.workflow.ExecutionPolicy;
import africa.civitas.egen.domain.workflow.RetryPolicy;
import africa.civitas.egen.domain.workflow.RetryPolicy.BackoffStrategy;
import africa.civitas.egen.domain.workflow.Step;
import africa.civitas.egen.domain.workflow.StepId;
import africa.civitas.egen.domain.workflow.StepOperation;
import africa.civitas.egen.domain.workflow.StepResult;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DTO &lt;-&gt; domaine pour les workflows (voir
 * docs/architecture/11-moteur-de-workflow.md, "Definition declarative d'un
 * workflow"). Toute erreur de contenu leve {@link IllegalArgumentException}
 * (la couche REST la traduit en 400) — jamais une valeur par defaut
 * silencieuse, sauf celles documentees dans le doc 11
 * ({@link #DEFAULT_STEP_TIMEOUT}, strategie et parallelisme par defaut).
 */
public final class WorkflowMapper {

    /** Doc 11 : l'exemple de reference omet {@code timeout} sur plusieurs etapes. */
    public static final Duration DEFAULT_STEP_TIMEOUT = Duration.ofSeconds(30);

    private static final String EXPECTED_KIND = "WorkflowDefinition";
    private static final Pattern DURATION = Pattern.compile("^(\\d+)(s|m|h)$");

    private WorkflowMapper() {
    }

    // ------------------------------------------------------------------ DTO -> domaine

    public static WorkflowDefinition toDomain(WorkflowDefinitionDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Definition de workflow vide");
        }
        if (dto.kind != null && !EXPECTED_KIND.equals(dto.kind)) {
            throw new IllegalArgumentException("kind invalide : \"" + dto.kind + "\" — attendu \"" + EXPECTED_KIND + "\"");
        }
        if (dto.metadata == null || dto.metadata.name == null) {
            throw new IllegalArgumentException("metadata.name est obligatoire");
        }
        if (dto.spec == null || dto.spec.steps == null || dto.spec.steps.isEmpty()) {
            throw new IllegalArgumentException("spec.steps doit declarer au moins une etape");
        }
        List<Step> steps = new ArrayList<>();
        for (WorkflowStepDto stepDto : dto.spec.steps) {
            steps.add(toStep(stepDto));
        }
        return WorkflowDefinition.of(WorkflowId.of(dto.metadata.name), steps,
                toCompensationStrategy(dto.spec.compensationStrategy), toExecutionPolicy(dto.spec.executionPolicy));
    }

    private static Step toStep(WorkflowStepDto dto) {
        if (dto == null || dto.id == null) {
            throw new IllegalArgumentException("Chaque etape doit declarer un id");
        }
        if (dto.service == null) {
            throw new IllegalArgumentException("Etape \"" + dto.id + "\" : service est obligatoire");
        }
        List<StepId> dependsOn = new ArrayList<>();
        if (dto.dependsOn != null) {
            dto.dependsOn.forEach(d -> dependsOn.add(StepId.of(d)));
        }
        StepOperation compensation = null;
        if (dto.compensation != null) {
            if (dto.compensation.operation == null) {
                throw new IllegalArgumentException("Etape \"" + dto.id + "\" : compensation.operation est obligatoire");
            }
            compensation = StepOperation.parse(dto.compensation.operation);
        }
        return new Step(StepId.of(dto.id), ServiceId.of(dto.service), StepOperation.parse(dto.operation), dependsOn,
                dto.timeout == null ? DEFAULT_STEP_TIMEOUT : parseDuration(dto.timeout, "etape " + dto.id + " timeout"),
                toRetryPolicy(dto.retry, dto.id), compensation);
    }

    private static RetryPolicy toRetryPolicy(StepRetryDto dto, String stepId) {
        if (dto == null) {
            return RetryPolicy.none();
        }
        BackoffStrategy backoff = dto.backoff == null ? BackoffStrategy.FIXED : parseEnum(BackoffStrategy.class,
                dto.backoff, "etape " + stepId + " retry.backoff");
        return new RetryPolicy(dto.maxAttempts, backoff);
    }

    private static CompensationStrategy toCompensationStrategy(String raw) {
        return raw == null ? CompensationStrategy.BACKWARD
                : parseEnum(CompensationStrategy.class, raw, "spec.compensationStrategy");
    }

    private static ExecutionPolicy toExecutionPolicy(ExecutionPolicyDto dto) {
        if (dto == null || dto.globalTimeout == null) {
            throw new IllegalArgumentException("spec.executionPolicy.globalTimeout est obligatoire");
        }
        List<StepId> approvals = new ArrayList<>();
        if (dto.manualApprovalSteps != null) {
            dto.manualApprovalSteps.forEach(s -> approvals.add(StepId.of(s)));
        }
        return new ExecutionPolicy(parseDuration(dto.globalTimeout, "executionPolicy.globalTimeout"), approvals,
                dto.maxParallelSteps == null ? 1 : dto.maxParallelSteps);
    }

    /** Durees du doc 11 : entier suivi de {@code s}, {@code m} ou {@code h} (ex. "10s", "5m"). */
    static Duration parseDuration(String raw, String fieldName) {
        Matcher matcher = DURATION.matcher(raw == null ? "" : raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(fieldName + " invalide : \"" + raw
                    + "\" — attendu un entier suivi de s, m ou h (ex. \"10s\", \"5m\")");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            default -> Duration.ofHours(amount);
        };
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String fieldName) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " invalide : \"" + raw + "\"", e);
        }
    }

    // ------------------------------------------------------------------ domaine -> DTO

    public static WorkflowDefinitionDto toDto(WorkflowDefinition definition) {
        WorkflowDefinitionDto dto = new WorkflowDefinitionDto();
        dto.apiVersion = "egen.civitas.africa/v1";
        dto.kind = EXPECTED_KIND;
        dto.metadata = new MetadataDto();
        dto.metadata.name = definition.id().value();
        dto.spec = new WorkflowSpecDto();
        dto.spec.steps = new ArrayList<>();
        // Ordre topologique : sortie deterministe, independante de l'ordre de saisie.
        for (StepId stepId : definition.topologicalOrder()) {
            dto.spec.steps.add(toDto(definition.stepById(stepId).orElseThrow()));
        }
        dto.spec.compensationStrategy = definition.compensationStrategy().name().toLowerCase(Locale.ROOT);
        ExecutionPolicy policy = definition.executionPolicy();
        dto.spec.executionPolicy = new ExecutionPolicyDto();
        dto.spec.executionPolicy.globalTimeout = formatDuration(policy.globalTimeout());
        dto.spec.executionPolicy.manualApprovalSteps = policy.manualApprovalSteps().stream().map(StepId::value).toList();
        dto.spec.executionPolicy.maxParallelSteps = policy.maxParallelSteps();
        return dto;
    }

    private static WorkflowStepDto toDto(Step step) {
        WorkflowStepDto dto = new WorkflowStepDto();
        dto.id = step.id().value();
        dto.service = step.service().value();
        dto.operation = step.operation().raw();
        dto.dependsOn = step.dependsOn().stream().map(StepId::value).toList();
        dto.timeout = formatDuration(step.timeout());
        dto.retry = new StepRetryDto();
        dto.retry.maxAttempts = step.retry().maxAttempts();
        dto.retry.backoff = step.retry().backoff().name().toLowerCase(Locale.ROOT);
        step.compensationOperation().ifPresent(op -> {
            dto.compensation = new StepCompensationDto();
            dto.compensation.operation = op.raw();
        });
        return dto;
    }

    /** Plus grande unite entiere exacte (h, puis m, puis s) — relisible par {@link #parseDuration}. */
    static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds > 0 && seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds > 0 && seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    public static WorkflowExecutionDto toDto(WorkflowExecution execution) {
        WorkflowExecutionDto dto = new WorkflowExecutionDto();
        dto.executionId = execution.executionId().value();
        dto.workflowId = execution.workflowId().value();
        dto.status = execution.status().name();
        dto.startedAt = execution.startedAt().toString();
        dto.input = execution.input();
        dto.stepResults = new ArrayList<>();
        for (StepResult result : execution.stepResults()) {
            StepResultDto stepDto = new StepResultDto();
            stepDto.stepId = result.stepId().value();
            stepDto.status = result.status().name();
            stepDto.attempt = result.attempt();
            stepDto.startedAt = result.startedAt().toString();
            stepDto.completedAt = result.completedAt() == null ? null : result.completedAt().toString();
            stepDto.output = result.output();
            stepDto.error = result.errorMessage();
            dto.stepResults.add(stepDto);
        }
        return dto;
    }
}
