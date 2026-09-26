package africa.civitas.egen.adapter.consul;

import africa.civitas.egen.application.port.ConfigSet;
import africa.civitas.egen.application.port.ConfigWatch;
import africa.civitas.egen.application.port.ResolvedConfig;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de niveau 3 (voir docs/architecture/17-strategie-de-tests.md) : demarre
 * un vrai agent Consul en mode dev via Testcontainers et verifie le mapping
 * de {@link ConsulConfigurationAdapter} contre l'API KV reelle, y compris le
 * rechargement a chaud via blocking query.
 */
@Testcontainers
class ConsulConfigurationAdapterIT {

    @Container
    static final GenericContainer<?> CONSUL = new GenericContainer<>("hashicorp/consul:1.19")
            .withExposedPorts(8500)
            .withCommand("agent", "-dev", "-client=0.0.0.0")
            .waitingFor(Wait.forHttp("/v1/status/leader").forStatusCode(200))
            .withStartupTimeout(Duration.ofSeconds(60));

    private ConsulConfigurationAdapter adapter() {
        URI baseUri = URI.create("http://" + CONSUL.getHost() + ":" + CONSUL.getMappedPort(8500));
        return new ConsulConfigurationAdapter(baseUri, null);
    }

    private static final ServiceId ID = ServiceId.of("news-service");
    private static final TargetEnvironment ENV = TargetEnvironment.of("test");

    @Test
    void resolveOnAnUnpublishedKeyReturnsEmpty() {
        ResolvedConfig config = adapter().resolve(ServiceId.of("never-published"), ENV);
        assertTrue(config.values().isEmpty());
    }

    @Test
    void publishThenResolveRoundTripsTheValues() {
        ConsulConfigurationAdapter adapter = adapter();
        adapter.publish(ID, ENV, new ConfigSet(Map.of("FEATURE_FLAG_COMMENTS", "true")));

        ResolvedConfig resolved = adapter.resolve(ID, ENV);

        assertEquals("true", resolved.values().get("FEATURE_FLAG_COMMENTS"));
        assertTrue(resolved.version().index() > 0);
    }

    @Test
    void watchIsNotifiedWhenTheConfigurationChanges() throws InterruptedException {
        ConsulConfigurationAdapter adapter = adapter();
        adapter.publish(ID, ENV, new ConfigSet(Map.of("FEATURE_FLAG_COMMENTS", "false")));

        BlockingQueue<ResolvedConfig> notifications = new ArrayBlockingQueue<>(1);
        ConfigWatch watch = adapter.watch(ID, ENV, notifications::add);

        // Laisse le watch etablir sa premiere blocking query avant de publier
        // le changement qu'il doit detecter.
        Thread.sleep(500);
        adapter.publish(ID, ENV, new ConfigSet(Map.of("FEATURE_FLAG_COMMENTS", "true")));

        ResolvedConfig changed = notifications.poll(10, TimeUnit.SECONDS);
        assertNotNull(changed, "le watch doit etre notifie du changement");
        assertEquals("true", changed.values().get("FEATURE_FLAG_COMMENTS"));

        adapter.unwatch(watch);
    }
}
