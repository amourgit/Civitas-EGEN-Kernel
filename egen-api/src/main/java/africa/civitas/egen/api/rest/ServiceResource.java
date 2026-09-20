package africa.civitas.egen.api.rest;

import africa.civitas.egen.api.dto.DeclareResponseDto;
import africa.civitas.egen.api.dto.ServiceManifestDto;
import africa.civitas.egen.api.dto.ServiceStatusDto;
import africa.civitas.egen.api.mapper.ServiceManifestMapper;
import africa.civitas.egen.application.usecase.DeclareResult;
import africa.civitas.egen.application.usecase.DeployServiceUseCase;
import africa.civitas.egen.application.usecase.GetServiceStatusUseCase;
import africa.civitas.egen.application.usecase.ServiceNotFoundException;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.TargetEnvironment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
 */
@Path("/api/v1/services")
public class ServiceResource {

    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

    private final DeployServiceUseCase deployServiceUseCase;
    private final GetServiceStatusUseCase getServiceStatusUseCase;
    private final String defaultEnvironment;

    @Inject
    public ServiceResource(DeployServiceUseCase deployServiceUseCase,
                            GetServiceStatusUseCase getServiceStatusUseCase,
                            @ConfigProperty(name = "egen.environment", defaultValue = "development")
                            String defaultEnvironment) {
        this.deployServiceUseCase = deployServiceUseCase;
        this.getServiceStatusUseCase = getServiceStatusUseCase;
        this.defaultEnvironment = defaultEnvironment;
    }

    @POST
    @Consumes({"application/yaml", "application/x-yaml"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response declare(String manifestYaml, @QueryParam("environment") String environment) {
        ServiceManifestDto dto;
        try {
            dto = YAML_MAPPER.readValue(manifestYaml, ServiceManifestDto.class);
        } catch (IOException e) {
            throw new BadRequestException("Manifeste YAML illisible : " + e.getMessage());
        }

        ServiceManifest manifest;
        try {
            manifest = ServiceManifestMapper.toDomain(dto);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        TargetEnvironment targetEnvironment = TargetEnvironment.of(
                environment != null && !environment.isBlank() ? environment : defaultEnvironment);

        DeclareResult result = deployServiceUseCase.declare(manifest, targetEnvironment);
        String statusUrl = "/api/v1/services/" + result.serviceId().value() + "/status";

        return Response.accepted(
                        new DeclareResponseDto(result.serviceId().value(), result.generation(), statusUrl))
                .header("Location", statusUrl)
                .build();
    }

    @GET
    @Path("/{id}/status")
    @Produces(MediaType.APPLICATION_JSON)
    public ServiceStatusDto status(@PathParam("id") String id) {
        try {
            return ServiceManifestMapper.toDto(getServiceStatusUseCase.getStatus(ServiceId.of(id)));
        } catch (ServiceNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
