package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.resilience.CachingResiliencyPolicyProvider;
import africa.civitas.egen.application.resilience.RegistryResiliencyPolicyProvider;
import africa.civitas.egen.application.resilience.ResiliencyPolicyProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;

/**
 * Cablage de la politique de resilience des services cibles (voir
 * docs/architecture/12-communication-fabric.md). Source : le manifeste desire
 * du Registry, derriere un cache court pour ne pas faire un aller-retour
 * PostgreSQL par appel HTTP du Moteur de Workflow ou par {@code discover}.
 */
@ApplicationScoped
public class ResilienceBeans {

    @Inject
    RegistryStorePort registryStorePort;

    @ConfigProperty(name = "egen.resilience.policy-cache-ttl-seconds", defaultValue = "5")
    long policyCacheTtlSeconds;

    @Produces
    @ApplicationScoped
    public ResiliencyPolicyProvider resiliencyPolicyProvider() {
        return new CachingResiliencyPolicyProvider(new RegistryResiliencyPolicyProvider(registryStorePort),
                Clock.systemUTC(), Duration.ofSeconds(policyCacheTtlSeconds));
    }
}
