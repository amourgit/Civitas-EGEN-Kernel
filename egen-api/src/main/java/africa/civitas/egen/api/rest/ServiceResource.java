package africa.civitas.egen.api.rest;

import africa.civitas.egen.api.dto.DeclareResponseDto;
import africa.civitas.egen.api.dto.ServiceManifestDto;
import africa.civitas.egen.api.dto.ServiceManifestResponseDto;
import africa.civitas.egen.api.dto.ServiceStatusDto;
import africa.civitas.egen.api.mapper.ServiceManifestMapper;
import africa.civitas.egen.api.mapper.YamlMappers;
import africa.civitas.egen.api.security.EgenSecurityContext;
import africa.civitas.egen.api.security.IdempotencyRequired;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.port.TraceSpan;
import africa.civitas.egen.application.usecase.DeclareResult;
import africa.civitas.egen.application.usecase.DeploymentAdapterChangeException;
import africa.civitas.egen.application.usecase.DeployServiceUseCase;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCase;
import africa.civitas.egen.application.usecase.ReadServiceManifestUseCase;
import africa.civitas.egen.application.usecase.ServiceNotFoundException;
import africa.civitas.egen.application.usecase.StopServiceUseCase;
import africa.civitas.egen.application.usecase.UnknownDeploymentAdapterException;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.TargetEnvironment;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.IOException;
import java.util.List;

/**
 * Adapter primaire REST — ressources de premier niveau "services" (voir
 * docs/architecture/13-api-et-contrats.md). Ne fait jamais attendre la
 * convergence du deploiement : POST repond 202 Accepted immediatement (voir
 * docs/architecture/04-moteur-de-reconciliation.md, "Anti-pattern a
 * bannir").
 *
 * <p>{@link Authenticated} : aucune methode de cette classe n'est
 * accessible sans jeton OIDC valide (voir docs/architecture/14-securite.md).
 * La politique HTTP par defaut (voir application.properties,
 * {@code quarkus.http.auth.permission.*}) refuse deja tout par defaut ;
 * cette annotation est une seconde ligne de defense explicite, pas la
 * seule (defense en profondeur, "zero confiance implicite" —
 * docs/architecture/14-securite.md).</p>
 */
@Path("/api/v1/services")
@Authenticated
public class ServiceResource {


    private final DeployServiceUseCase deployServiceUseCase;
    private final GetServiceStatusUseCase getServiceStatusUseCase;
    private final ReadServiceManifestUseCase readServiceManifestUseCase;
    private final StopServiceUseCase stopServiceUseCase;
    private final ObservabilityPort observabilityPort;
    private final EgenSecurityContext securityContext;
    private final String defaultEnvironment;

    @Inject
    public ServiceResource(DeployServiceUseCase deployServiceUseCase,
                            GetServiceStatusUseCase getServiceStatusUseCase,
                            ReadServiceManifestUseCase readServiceManifestUseCase,
                            StopServiceUseCase stopServiceUseCase,
                            ObservabilityPort observabilityPort,
                            EgenSecurityContext securityContext,
                            @ConfigProperty(name = "egen.environment", defaultValue = "development")
                            String defaultEnvironment) {
        this.deployServiceUseCase = deployServiceUseCase;
        this.getServiceStatusUseCase = getServiceStatusUseCase;
        this.readServiceManifestUseCase = readServiceManifestUseCase;
        this.stopServiceUseCase = stopServiceUseCase;
        this.observabilityPort = observabilityPort;
        this.securityContext = securityContext;
        this.defaultEnvironment = defaultEnvironment;
    }

    @POST
    @IdempotencyRequired
    @Consumes({"application/yaml", "application/x-yaml"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response declare(String manifestYaml, @QueryParam("environment") String environment,
                             @HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.declare", traceparent)) {
            try {
                // Modifier = administrateur (docs/architecture/14-securite.md), verifie AVANT toute lecture du
                // corps : un non-administrateur recoit 403 sans que le YAML soit meme analyse.
                securityContext.assertAdmin();

                ServiceManifestDto dto;
                try {
                    dto = YamlMappers.read(manifestYaml, ServiceManifestDto.class);
                } catch (IOException e) {
                    throw new BadRequestException("Manifeste YAML illisible : " + e.getMessage());
                }

                ServiceManifest manifest;
                try {
                    manifest = ServiceManifestMapper.toDomain(dto);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException(e.getMessage());
                }
                span.setAttribute("service.id", manifest.id().value());

                TargetEnvironment targetEnvironment = TargetEnvironment.of(
                        environment != null && !environment.isBlank() ? environment : defaultEnvironment);

                // span.context() propage jusqu'au premier cycle de
                // reconciliation qui en resulte, meme quand l'appelant n'a
                // fourni aucun traceparent (le span http.declare lui-meme
                // sert alors de racine a lier) — voir
                // PendingDeclareTraces et docs/architecture/15-observabilite.md.
                DeclareResult result;
                try {
                    result = deployServiceUseCase.declare(manifest, targetEnvironment, span.context());
                } catch (UnknownDeploymentAdapterException e) {
                    // deployment.adapter désigne un adapter non activé : requête invalide (06, validation, étape 3).
                    throw new BadRequestException(e.getMessage());
                } catch (DeploymentAdapterChangeException e) {
                    // L'adapter d'un service existant est immuable (07, « Sélection et routage »).
                    throw new ClientErrorException(e.getMessage(), Response.Status.CONFLICT);
                }
                String statusUrl = "/api/v1/services/" + result.serviceId().value() + "/status";

                return Response.accepted(
                                new DeclareResponseDto(result.serviceId().value(), result.generation(), statusUrl))
                        .header("Location", statusUrl)
                        .build();
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    @GET
    @Path("/{id}/status")
    @Produces(MediaType.APPLICATION_JSON)
    public ServiceStatusDto status(@PathParam("id") String id, @HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.getStatus", traceparent)) {
            span.setAttribute("service.id", id);
            try {
                ServiceId serviceId = ServiceId.of(id);
                return ServiceManifestMapper.toDto(getServiceStatusUseCase.getStatus(serviceId));
            } catch (ServiceNotFoundException e) {
                throw new NotFoundException(e.getMessage());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    /**
     * Action imperative explicite, rare — la norme reste declarative (voir
     * docs/architecture/13-api-et-contrats.md). Ne fait jamais attendre
     * l'arret reel : fait passer le service en STOPPING et laisse la
     * boucle de reconciliation executer l'ordre precis deregister -> grace
     * period -> stop() (voir docs/architecture/09-cycle-de-vie.md, §9.3).
     */
    @POST
    @IdempotencyRequired
    @Path("/{id}/actions/stop")
    public Response stop(@PathParam("id") String id, @HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.stop", traceparent)) {
            span.setAttribute("service.id", id);
            try {
                // Modifier = administrateur, verifie avant de chercher le service : 403 meme pour un id inconnu.
                securityContext.assertAdmin();
                ServiceId serviceId = ServiceId.of(id);
                stopServiceUseCase.stop(serviceId, span.context());
                return Response.accepted().build();
            } catch (ServiceNotFoundException e) {
                throw new NotFoundException(e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new BadRequestException(e.getMessage());
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    /**
     * Lecture du manifeste declare (docs/architecture/13, CRUD sur {@code /api/v1/services}) : ouverte a tout
     * appelant authentifie, comme {@code status} ; {@code metadata.team} est restitue tel quel.
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ServiceManifestResponseDto read(@PathParam("id") String id, @HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.getManifest", traceparent)) {
            span.setAttribute("service.id", id);
            try {
                ServiceId serviceId = ServiceId.of(id);
                DesiredState desired = readServiceManifestUseCase.get(serviceId);
                return toResponse(desired);
            } catch (ServiceNotFoundException e) {
                throw new NotFoundException(e.getMessage());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    /** Tous les manifestes declares : la lecture est ouverte a tout appelant authentifie (aucun filtre d'equipe). */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ServiceManifestResponseDto> list(@HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.listManifests", traceparent)) {
            try {
                return readServiceManifestUseCase.list().stream()
                        .map(ServiceResource::toResponse)
                        .toList();
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    private static ServiceManifestResponseDto toResponse(DesiredState desired) {
        return new ServiceManifestResponseDto(desired.manifest().id().value(), desired.generation(),
                ServiceManifestMapper.toDto(desired.manifest()));
    }

    /**
     * Ouvre le span racine (ou enfant, si un traceparent entrant valide est
     * fourni) de la requete HTTP elle-meme (voir
     * docs/architecture/15-observabilite.md, "chaque appel entrant sur
     * l'API de controle propage son traceparent"). Les appels synchrones
     * faits dans le corps de la methode (RegistryStorePort via son
     * decorateur ObservedRegistryStorePort, voir egen-application.observability)
     * restent mesures en duree/erreur independamment ; ce span-ci couvre le
     * traitement HTTP dans son ensemble, distinct du span racine propre a
     * chaque cycle de reconciliation asynchrone (docs/architecture/15-observabilite.md).
     */
    private TraceSpan startHttpSpan(String operationName, String traceparent) {
        TraceContext parent = (traceparent == null || traceparent.isBlank()) ? null : new TraceContext(traceparent);
        return observabilityPort.startSpan(operationName, parent);
    }
}
