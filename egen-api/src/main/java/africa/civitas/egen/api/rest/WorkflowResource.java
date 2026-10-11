package africa.civitas.egen.api.rest;

import africa.civitas.egen.api.dto.ExecutionAcceptedDto;
import africa.civitas.egen.api.dto.RunWorkflowRequestDto;
import africa.civitas.egen.api.dto.TerminateRequestDto;
import africa.civitas.egen.api.dto.WorkflowDefinitionDto;
import africa.civitas.egen.api.dto.WorkflowExecutionDto;
import africa.civitas.egen.api.mapper.WorkflowMapper;
import africa.civitas.egen.api.security.EgenSecurityContext;
import africa.civitas.egen.api.security.IdempotencyRequired;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.port.TraceSpan;
import africa.civitas.egen.application.usecase.DefineWorkflowUseCase;
import africa.civitas.egen.application.usecase.RunWorkflowUseCase;
import africa.civitas.egen.application.usecase.WorkflowNotFoundException;
import africa.civitas.egen.application.workflow.ExecutionNotFoundException;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.IllegalExecutionTransitionException;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowId;
import africa.civitas.egen.domain.workflow.WorkflowInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.Map;

/**
 * Adapter primaire REST — ressources de premier niveau "workflows" (voir
 * docs/architecture/13-api-et-contrats.md). Comme {@link ServiceResource},
 * ne fait jamais attendre le resultat d'une mutation : toute mutation
 * repond {@code 202 Accepted} + {@code Location} vers la ressource de
 * suivi (voir docs/architecture/04-moteur-de-reconciliation.md,
 * "Anti-pattern a bannir").
 *
 * <p><b>Autorisation</b> : lecture pour tout appelant authentifie ;
 * toute mutation est reservee aux administrateurs ({@link
 * EgenSecurityContext#assertAdmin()}) — une definition de workflow ne
 * porte aucune equipe proprietaire (voir docs/architecture/14-securite.md).</p>
 *
 * <p><b>Codes d'erreur</b> : contenu invalide ou identifiant mal forme
 * &rarr; 400 ; definition ou execution inconnue (ou execution d'un
 * autre workflow) &rarr; 404 ; transition d'etat impossible (ex. reprendre
 * une execution deja terminee) &rarr; 409.</p>
 */
@Path("/api/v1/workflows")
@Authenticated
public class WorkflowResource {

    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final DefineWorkflowUseCase defineWorkflowUseCase;
    private final RunWorkflowUseCase runWorkflowUseCase;
    private final ObservabilityPort observabilityPort;
    private final EgenSecurityContext securityContext;

    @Inject
    public WorkflowResource(DefineWorkflowUseCase defineWorkflowUseCase, RunWorkflowUseCase runWorkflowUseCase,
                            ObservabilityPort observabilityPort, EgenSecurityContext securityContext) {
        this.defineWorkflowUseCase = defineWorkflowUseCase;
        this.runWorkflowUseCase = runWorkflowUseCase;
        this.observabilityPort = observabilityPort;
        this.securityContext = securityContext;
    }

    // ------------------------------------------------------------------ definitions

    @POST
    @IdempotencyRequired
    @Consumes({"application/yaml", "application/x-yaml"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response define(String workflowYaml, @HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.defineWorkflow", traceparent)) {
            try {
                securityContext.assertAdmin();
                WorkflowDefinitionDto dto;
                try {
                    dto = YAML_MAPPER.readValue(workflowYaml, WorkflowDefinitionDto.class);
                } catch (IOException e) {
                    throw new BadRequestException("Definition YAML illisible : " + e.getMessage());
                }
                WorkflowDefinition definition;
                try {
                    definition = WorkflowMapper.toDomain(dto);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException(e.getMessage());
                }
                span.setAttribute("workflow.id", definition.id().value());
                defineWorkflowUseCase.define(definition);
                String location = "/api/v1/workflows/" + definition.id().value();
                return Response.accepted(WorkflowMapper.toDto(definition)).header("Location", location).build();
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public WorkflowDefinitionDto get(@PathParam("id") String id, @HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.getWorkflow", traceparent)) {
            span.setAttribute("workflow.id", id);
            try {
                return WorkflowMapper.toDto(defineWorkflowUseCase.get(WorkflowId.of(id)));
            } catch (WorkflowNotFoundException e) {
                throw new NotFoundException(e.getMessage());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------ executions

    /**
     * Le corps est optionnel (un workflow sans entree est valide) : lu comme
     * texte puis interprete ici, pour qu'une requete sans corps ni
     * Content-Type reste acceptee plutot que rejetee en 415.
     */
    @POST
    @IdempotencyRequired
    @Path("/{id}/executions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response run(@PathParam("id") String id, String body, @HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.runWorkflow", traceparent)) {
            span.setAttribute("workflow.id", id);
            try {
                securityContext.assertAdmin();
                WorkflowInput input = parseInput(body);
                ExecutionId executionId = runWorkflowUseCase.run(WorkflowId.of(id), input);
                String statusUrl = "/api/v1/workflows/" + id + "/executions/" + executionId.value();
                return Response.accepted(new ExecutionAcceptedDto(executionId.value(), statusUrl))
                        .header("Location", statusUrl).build();
            } catch (WorkflowNotFoundException e) {
                throw new NotFoundException(e.getMessage());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    @GET
    @Path("/{id}/executions/{executionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public WorkflowExecutionDto observe(@PathParam("id") String id, @PathParam("executionId") String executionId,
                                         @HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.getWorkflowExecution", traceparent)) {
            span.setAttribute("workflow.id", id);
            span.setAttribute("workflow.execution.id", executionId);
            try {
                return WorkflowMapper.toDto(ownedExecution(id, executionId));
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    @POST
    @IdempotencyRequired
    @Path("/{id}/executions/{executionId}/actions/pause")
    public Response pause(@PathParam("id") String id, @PathParam("executionId") String executionId,
                          @HeaderParam("traceparent") String traceparent) {
        return action("http.pauseWorkflowExecution", id, executionId, traceparent,
                runWorkflowUseCase::pause);
    }

    @POST
    @IdempotencyRequired
    @Path("/{id}/executions/{executionId}/actions/resume")
    public Response resume(@PathParam("id") String id, @PathParam("executionId") String executionId,
                           @HeaderParam("traceparent") String traceparent) {
        return action("http.resumeWorkflowExecution", id, executionId, traceparent,
                runWorkflowUseCase::resume);
    }

    /** Corps optionnel {@code {"reason": "..."}}, lu comme texte pour la meme raison que {@link #run}. */
    @POST
    @IdempotencyRequired
    @Path("/{id}/executions/{executionId}/actions/terminate")
    public Response terminate(@PathParam("id") String id, @PathParam("executionId") String executionId,
                              String body, @HeaderParam("traceparent") String traceparent) {
        String reason = parseReason(body);
        return action("http.terminateWorkflowExecution", id, executionId, traceparent,
                execution -> runWorkflowUseCase.terminate(execution, reason));
    }

    // ------------------------------------------------------------------ interne

    private Response action(String spanName, String id, String executionId, String traceparent,
                            java.util.function.Consumer<ExecutionId> operation) {
        try (TraceSpan span = startHttpSpan(spanName, traceparent)) {
            span.setAttribute("workflow.id", id);
            span.setAttribute("workflow.execution.id", executionId);
            try {
                securityContext.assertAdmin();
                ExecutionId execution = ownedExecution(id, executionId).executionId();
                operation.accept(execution);
                return Response.accepted()
                        .header("Location", "/api/v1/workflows/" + id + "/executions/" + executionId).build();
            } catch (IllegalExecutionTransitionException e) {
                throw new WebApplicationException(e.getMessage(), Response.Status.CONFLICT);
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    /**
     * Une execution n'est adressable que sous SON workflow : demander
     * l'execution d'un autre workflow est un 404, jamais une fuite de son
     * contenu.
     */
    private WorkflowExecution ownedExecution(String workflowId, String executionId) {
        try {
            WorkflowId expected = WorkflowId.of(workflowId);
            WorkflowExecution execution = runWorkflowUseCase.observe(ExecutionId.of(executionId));
            if (!execution.workflowId().equals(expected)) {
                throw new NotFoundException("Aucune execution " + executionId + " pour le workflow " + workflowId);
            }
            return execution;
        } catch (ExecutionNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static WorkflowInput parseInput(String body) {
        if (body == null || body.isBlank()) {
            return WorkflowInput.empty();
        }
        try {
            RunWorkflowRequestDto request = JSON_MAPPER.readValue(body, RunWorkflowRequestDto.class);
            Map<String, String> input = request == null ? null : request.input;
            return input == null ? WorkflowInput.empty() : new WorkflowInput(input);
        } catch (IOException e) {
            throw new BadRequestException("Corps JSON illisible (attendu {\"input\": {\"cle\": \"valeur\"}}) : "
                    + e.getMessage());
        }
    }

    private static String parseReason(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            TerminateRequestDto request = JSON_MAPPER.readValue(body, TerminateRequestDto.class);
            return request == null ? null : request.reason;
        } catch (IOException e) {
            throw new BadRequestException("Corps JSON illisible (attendu {\"reason\": \"...\"}) : " + e.getMessage());
        }
    }

    /** Meme raisonnement que ServiceResource#startHttpSpan (voir docs/architecture/15-observabilite.md). */
    private TraceSpan startHttpSpan(String operationName, String traceparent) {
        TraceContext parent = (traceparent == null || traceparent.isBlank()) ? null : new TraceContext(traceparent);
        return observabilityPort.startSpan(operationName, parent);
    }
}
