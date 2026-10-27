package africa.civitas.egen.api.rest;

import africa.civitas.egen.api.dto.PublishEventRequestDto;
import africa.civitas.egen.api.dto.PublishEventResponseDto;
import africa.civitas.egen.api.security.EgenSecurityContext;
import africa.civitas.egen.api.security.IdempotencyRequired;
import africa.civitas.egen.application.port.MessagingException;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.port.TraceSpan;
import africa.civitas.egen.application.usecase.EventPublicationNotAllowedException;
import africa.civitas.egen.application.usecase.PublishEventCommand;
import africa.civitas.egen.application.usecase.PublishEventUseCase;
import africa.civitas.egen.domain.event.TechnicalEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;

/**
 * Point d'entree generique de publication d'un evenement technique (voir docs/architecture/13-api-et-contrats.md
 * et 12-communication-fabric.md). Le Kernel relaie vers le Messaging Port sans jamais lire {@code data}.
 *
 * <p><b>Autorisation</b> : un appelant ne publie que ce qu'un manifeste annonce ({@code events.publishes}) ;
 * {@code 403} si aucun service declare ne publie ce type, ou si l'appelant n'est autorise (equipe proprietaire,
 * ou administrateur) sur aucun de ses publieurs — meme regle que {@code Declare} (docs/architecture/14-securite.md).
 * <b>Erreurs</b> : corps illisible ou incomplet &rarr; 400 ; broker indisponible &rarr; 503 (rien n'a ete publie).</p>
 */
@Path("/api/v1/events")
@Authenticated
public class EventResource {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final PublishEventUseCase publishEventUseCase;
    private final ObservabilityPort observabilityPort;
    private final EgenSecurityContext securityContext;

    @Inject
    public EventResource(PublishEventUseCase publishEventUseCase, ObservabilityPort observabilityPort,
                         EgenSecurityContext securityContext) {
        this.publishEventUseCase = publishEventUseCase;
        this.observabilityPort = observabilityPort;
        this.securityContext = securityContext;
    }

    @POST
    @IdempotencyRequired
    @Path("/publish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response publish(String body, @HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.publishEvent", traceparent)) {
            try {
                PublishEventCommand command = toCommand(body);
                span.setAttribute("event.type", command.type());
                TechnicalEvent event = publishEventUseCase.publish(command, securityContext::ownsOrAdmin);
                return Response.accepted(
                        new PublishEventResponseDto(event.id(), event.type(), event.correlationId())).build();
            } catch (EventPublicationNotAllowedException e) {
                throw new ForbiddenException(e.getMessage());
            } catch (MessagingException e) {
                throw new ServiceUnavailableException("Messagerie indisponible : rien n'a ete publie");
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    private static PublishEventCommand toCommand(String body) {
        PublishEventRequestDto dto;
        try {
            dto = JSON_MAPPER.readValue(body == null ? "" : body, PublishEventRequestDto.class);
        } catch (IOException e) {
            throw new BadRequestException("Evenement JSON illisible : " + e.getMessage());
        }
        if (dto == null) {
            throw new BadRequestException("Corps vide : un evenement est attendu");
        }
        if (dto.specversion != null && !"1.0".equals(dto.specversion)) {
            throw new BadRequestException("specversion non supportee : " + dto.specversion + " (attendu : 1.0)");
        }
        if (dto.data == null) {
            throw new BadRequestException("data est obligatoire");
        }
        try {
            return new PublishEventCommand(dto.type, dto.source, dto.correlationId, dto.causationId,
                    dto.data.toString());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /** Meme raisonnement que ServiceResource#startHttpSpan (voir docs/architecture/15-observabilite.md). */
    private TraceSpan startHttpSpan(String operationName, String traceparent) {
        TraceContext parent = (traceparent == null || traceparent.isBlank()) ? null : new TraceContext(traceparent);
        return observabilityPort.startSpan(operationName, parent);
    }
}
