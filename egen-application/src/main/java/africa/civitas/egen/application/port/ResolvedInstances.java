package africa.civitas.egen.application.port;

import java.util.List;

/**
 * Retour de {@link DiscoveryPort#resolve} — ne contient QUE des instances
 * saines (mapping Consul : {@code ?passing=true}, voir 07.2.2). Consomme
 * directement par le proxy de lecture "/api/v1/discover/{serviceName}"
 * (voir docs/architecture/13-api-et-contrats.md).
 */
public record ResolvedInstances(List<ResolvedInstance> instances) {

    public ResolvedInstances {
        instances = instances == null ? List.of() : List.copyOf(instances);
    }

    public static ResolvedInstances empty() {
        return new ResolvedInstances(List.of());
    }
}
