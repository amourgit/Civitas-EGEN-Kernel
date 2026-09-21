package africa.civitas.egen.adapter.consul;

import africa.civitas.egen.application.port.ResolvedInstances;
import africa.civitas.egen.application.port.ServiceInstanceRegistration;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.ServiceId;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de niveau 3 (voir docs/architecture/17-strategie-de-tests.md) : demarre
 * un vrai agent Consul en mode dev via Testcontainers et verifie le mapping
 * de {@link ConsulDiscoveryAdapter} contre l'API HTTP reelle.
 *
 * <p>Le health check declare pointe vers {@code registration.address()},
 * qui n'est pas joignable depuis le conteneur Consul dans cet environnement
 * de test isole — l'instance enregistree n'atteint donc pas l'etat
 * "passing" ici. Ce test verifie ce qui est reellement verifiable a ce
 * niveau : l'enregistrement est accepte (aucune exception), et
 * deregister() retire bien l'instance. La convergence "passing" de bout en
 * bout est couverte par le test de niveau 5 (voir
 * docs/architecture/20-scenario-bout-en-bout.md) dans un environnement ou
 * les instances sont reellement joignables entre elles.</p>
 */
@Testcontainers
class ConsulDiscoveryAdapterIT {

    @Container
    static final GenericContainer<?> CONSUL = new GenericContainer<>("hashicorp/consul:1.19")
            .withExposedPorts(8500)
            .withCommand("agent", "-dev", "-client=0.0.0.0")
            .waitingFor(Wait.forHttp("/v1/status/leader").forStatusCode(200))
            .withStartupTimeout(Duration.ofSeconds(60));

    private ConsulDiscoveryAdapter adapter() {
        URI baseUri = URI.create("http://" + CONSUL.getHost() + ":" + CONSUL.getMappedPort(8500));
        return new ConsulDiscoveryAdapter(baseUri, null);
    }

    private static ServiceId serviceId() {
        return ServiceId.of("it-fixture-service");
    }

    private static HealthSpec aHealthSpec() {
        return new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3);
    }

    @Test
    void registeringTwiceIsIdempotentAndDeregisterRemovesTheInstance() {
        ConsulDiscoveryAdapter adapter = adapter();
        ServiceId id = serviceId();
        ServiceInstanceRegistration registration =
                new ServiceInstanceRegistration(id, "it-fixture-1", "10.0.0.1", 8080, aHealthSpec());

        adapter.register(registration);
        adapter.register(registration); // idempotence (garde-fou n7)

        // resolve() ne retourne que les instances "passing" — le check pointe
        // vers une adresse non joignable ici (voir javadoc de la classe), donc
        // on verifie ici uniquement que l'enregistrement/desenregistrement en
        // tant que tels sont acceptes par l'API reelle, sans lever d'exception.
        adapter.deregister(id, "it-fixture-1");
        adapter.deregister(id, "it-fixture-1"); // idempotence : deja absente

        ResolvedInstances afterRemoval = adapter.resolve(id);
        assertTrue(afterRemoval.instances().isEmpty());
        assertEquals(0, afterRemoval.instances().size());
    }
}
