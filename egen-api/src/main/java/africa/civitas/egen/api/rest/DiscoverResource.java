package africa.civitas.egen.api.rest;

import africa.civitas.egen.api.dto.DiscoverResponseDto;
import africa.civitas.egen.api.dto.ResolvedInstanceDto;
import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.ResolvedInstance;
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

    private final DiscoveryPort discoveryPort;

    @Inject
    public DiscoverResource(DiscoveryPort discoveryPort) {
        this.discoveryPort = discoveryPort;
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
        var instances = discoveryPort.resolve(id).instances().stream()
                .map(DiscoverResource::toDto)
                .toList();
        return new DiscoverResponseDto(id.value(), instances);
    }

    private static ResolvedInstanceDto toDto(ResolvedInstance instance) {
        return new ResolvedInstanceDto(instance.instanceId(), instance.address(), instance.port());
    }
}
