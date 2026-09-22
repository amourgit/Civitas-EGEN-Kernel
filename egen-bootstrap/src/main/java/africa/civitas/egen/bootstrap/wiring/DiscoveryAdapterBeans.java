package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.consul.ConsulDiscoveryAdapter;
import africa.civitas.egen.application.port.DiscoveryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Optional;

/**
 * Seul point du Kernel qui sait que le DiscoveryPort est, en V1, implemente
 * par Consul (voir docs/architecture/07-ports-et-adapters.md). Remplacer ou
 * ajouter un second Discovery Adapter se fait en changeant uniquement cette
 * methode (garde-fou n6, docs/architecture/02-principes-fondamentaux.md).
 */
@ApplicationScoped
public class DiscoveryAdapterBeans {

    @Produces
    @ApplicationScoped
    // Voir DeploymentAdapterBeans.deploymentPort() : meme justification pour
    // Optional<String> ici — c'est le mode d'injection officiel de
    // MicroProfile Config pour une propriete sans defaultValue.
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public DiscoveryPort discoveryPort(
            @ConfigProperty(name = "egen.consul.address", defaultValue = "http://localhost:8500")
            String consulAddress,
            @ConfigProperty(name = "egen.consul.token")
            Optional<String> consulToken) {
        return new ConsulDiscoveryAdapter(URI.create(consulAddress), consulToken.orElse(null));
    }
}
