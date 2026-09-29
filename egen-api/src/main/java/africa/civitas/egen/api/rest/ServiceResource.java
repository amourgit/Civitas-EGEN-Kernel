package africa.civitas.egen.api.rest;

import africa.civitas.egen.api.dto.DeclareResponseDto;
import africa.civitas.egen.api.dto.ServiceManifestDto;
import africa.civitas.egen.api.dto.ServiceStatusDto;
import africa.civitas.egen.api.mapper.ServiceManifestMapper;
import africa.civitas.egen.api.security.EgenSecurityContext;
import africa.civitas.egen.api.security.IdempotencyRequired;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.port.TraceSpan;
import africa.civitas.egen.application.usecase.DeclareResult;
import africa.civitas.egen.application.usecase.DeployServiceUseCase;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCase;
import africa.civitas.egen.application.usecase.ServiceNotFoundException;
import africa.civitas.egen.application.usecase.StopServiceUseCase;
import africa.civitas.egen.domain.model.OwnerTeam;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.TargetEnvironment;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;

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

    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

    private final DeployServiceUseCase deployServiceUseCase;
    private final GetServiceStatusUseCase getServiceStatusUseCase;
    private final StopServiceUseCase stopServiceUseCase;
    private final RegistryStorePort registryStorePort;
    private final ObservabilityPort observabilityPort;
    private final EgenSecurityContext securityContext;
    private final String defaultEnvironment;

    @Inject
    public ServiceResource(DeployServiceUseCase deployServiceUseCase,
                            GetServiceStatusUseCase getServiceStatusUseCase,
                            StopServiceUseCase stopServiceUseCase,
                            RegistryStorePort registryStorePort,
                            ObservabilityPort observabilityPort,
                            EgenSecurityContext securityContext,
                            @ConfigProperty(name = "egen.environment", defaultValue = "development")
                            String defaultEnvironment) {
        this.deployServiceUseCase = deployServiceUseCase;
        this.getServiceStatusUseCase = getServiceStatusUseCase;
        this.stopServiceUseCase = stopServiceUseCase;
        this.registryStorePort = registryStorePort;
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
                ServiceManifestDto dto;
                try {
                    dto = YAML_MAPPER.readValue(manifestYaml, ServiceManifestDto.class);
                } catch (IOException e) {
                    throw new BadRequestException("Manifeste YAML illisible : " + e.getMessage());
                }

                // RBAC avant toute construction/persistance (voir
                // docs/architecture/14-securite.md) : on verifie l'equipe
                // declaree dans le YAML brut, pas encore le domaine complet.
                securityContext.assertCanDeclare(ServiceManifestMapper.teamOf(dto));

                ServiceManifest manifest;
                try {
                    manifest = ServiceManifestMapper.toDomain(dto);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException(e.getMessage());
                }
                span.setAttribute("service.id", manifest.id().value());

                TargetEnvironment targetEnvironment = TargetEnvironment.of(
                        environment != null && !environment.isBlank() ? environment : defaultEnvironment);

                DeclareResult result = deployServiceUseCase.declare(manifest, targetEnvironment);
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
                assertOwnsOrAdminIfKnown(serviceId);
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
                ServiceId serviceId = ServiceId.of(id);
                assertOwnsOrAdminIfKnown(serviceId);
                stopServiceUseCase.stop(serviceId);
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
     * Verifie la propriete uniquement si le service existe deja dans le
     * Registry — un identifiant inconnu doit remonter 404 depuis le use
     * case, pas 403 depuis cette methode (voir EgenSecurityContext,
     * "Autorisation d'un Declare" pour le raisonnement inverse au moment
     * de la creation).
     */
    private void assertOwnsOrAdminIfKnown(ServiceId id) {
        registryStorePort.findById(id).ifPresent(desired -> {
            OwnerTeam ownerTeam = desired.manifest().ownerTeam();
            securityContext.assertOwnsOrAdmin(ownerTeam);
        });
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
