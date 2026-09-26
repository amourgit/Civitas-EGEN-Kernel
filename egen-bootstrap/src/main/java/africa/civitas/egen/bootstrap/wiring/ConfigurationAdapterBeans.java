package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.consul.ConsulConfigurationAdapter;
import africa.civitas.egen.application.port.ConfigurationPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Optional;

/**
 * Seul point du Kernel qui sait que le ConfigurationPort est, en V1,
 * implemente par Consul KV (voir docs/architecture/07-ports-et-adapters.md,
 * "Configuration Port"). Reutilise la meme adresse Consul que le Discovery
 * Adapter — un seul agent Consul sert les deux besoins (garde-fou n3,
 * docs/architecture/02-principes-fondamentaux.md : ne pas introduire un
 * systeme supplementaire quand un existant suffit).
 */
@ApplicationScoped
public class ConfigurationAdapterBeans {

    @Produces
    @ApplicationScoped
    public ConfigurationPort configurationPort(
            @ConfigProperty(name = "egen.consul.address", defaultValue = "http://localhost:8500")
            String consulAddress,
            @ConfigProperty(name = "egen.consul.token")
            Optional<String> consulToken) {
        return new ConsulConfigurationAdapter(URI.create(consulAddress), consulToken.orElse(null));
    }
}
