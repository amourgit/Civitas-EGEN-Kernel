package africa.civitas.egen.api.rest;

import africa.civitas.egen.api.dto.DependencyGraphDto;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.dependency.DependencyGraph;
import africa.civitas.egen.domain.model.Dependency;
import africa.civitas.egen.domain.model.ServiceId;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lecture du {@code DependencyGraph} global (voir
 * docs/architecture/13-api-et-contrats.md, "/api/v1/dependencies/graph") —
 * reconstruit a la volee depuis le Registry, jamais persiste separement
 * (le Registry reste l'unique source de verite declarative, voir
 * docs/architecture/08-registry.md).
 */
@Path("/api/v1/dependencies/graph")
public class DependencyGraphResource {

    private final RegistryStorePort registryStorePort;

    @Inject
    public DependencyGraphResource(RegistryStorePort registryStorePort) {
        this.registryStorePort = registryStorePort;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public DependencyGraphDto graph() {
        Map<ServiceId, Set<ServiceId>> edges = new HashMap<>();
        for (ServiceId id : registryStorePort.findAllIds()) {
            registryStorePort.findById(id).ifPresent(desired -> edges.put(id,
                    desired.manifest().dependencies().stream()
                            .map(Dependency::serviceId)
                            .collect(Collectors.toCollection(HashSet::new))));
        }

        DependencyGraph graph = DependencyGraph.of(edges);

        Map<String, java.util.List<String>> dependsOn = new HashMap<>();
        for (ServiceId node : graph.nodes()) {
            dependsOn.put(node.value(), graph.dependenciesOf(node).stream()
                    .map(ServiceId::value).sorted().toList());
        }

        return new DependencyGraphDto(
                graph.nodes().stream().map(ServiceId::value).sorted().toList(),
                dependsOn,
                graph.topologicalOrder().stream().map(ServiceId::value).toList());
    }
}
