package africa.civitas.egen.api.rest;

import africa.civitas.egen.api.dto.DiscoverResponseDto;
import africa.civitas.egen.api.dto.ResolvedInstanceDto;
import africa.civitas.egen.api.mapper.ResiliencyMapper;
import africa.civitas.egen.application.port.ResolvedInstance;
import africa.civitas.egen.application.usecase.ResolveServiceUseCase;
import africa.civitas.egen.application.usecase.ServiceResolution;
import africa.civitas.egen.domain.model.ServiceId;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Proxy pratique en lecture seule vers {@code DiscoveryPort.resolve} (voir
 * docs/architecture/13-api-et-contrats.md, "/api/v1/discover/{serviceName}") —
 * permet a un systeme externe de resoudre un service sans jamais coder une
 * IP en dur (voir docs/architecture/20-scenario-bout-en-bout.md).
 */
@Path("/api/v1/discover")
public class DiscoverResource {

    private final ResolveServiceUseCase resolveServiceUseCase;

    @Inject
    public DiscoverResource(ResolveServiceUseCase resolveServiceUseCase) {
        this.resolveServiceUseCase = resolveServiceUseCase;
    }

    @GET
    @Path("/{serviceName}")
    @Produces(MediaType.APPLICATION_JSON)
    public DiscoverResponseDto discover(@PathParam("serviceName") String serviceName) {
        ServiceId id;
        try {
            id = ServiceId.of(serviceName);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        ServiceResolution resolution = resolveServiceUseCase.resolve(id);
        var instances = resolution.instances().instances().stream()
                .map(DiscoverResource::toDto)
                .toList();
        return new DiscoverResponseDto(id.value(), instances, ResiliencyMapper.toView(resolution.resiliency()));
    }

    private static ResolvedInstanceDto toDto(ResolvedInstance instance) {
        return new ResolvedInstanceDto(instance.instanceId(), instance.address(), instance.port());
    }
}

