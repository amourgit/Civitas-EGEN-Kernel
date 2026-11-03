package africa.civitas.egen.adapter.consul;

import africa.civitas.egen.application.port.ConfigSet;
import africa.civitas.egen.application.port.ConfigWatch;
import africa.civitas.egen.application.port.ConfigurationException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // ------------------------------------------------------------------ A3 (docs 13, « Publication de configuration »)
    // L'API de publication repose sur ces trois garanties du port : version 0 avant toute publication,
    // remplacement COMPLET du jeu de valeurs, version qui ne fait qu'augmenter.

    @Test
    void theVersionOfAnUnpublishedConfigurationIsZero() {
        assertEquals(0L, adapter().currentVersion(ServiceId.of("a3-never-published"), ENV).index());
    }

    @Test
    void publishReplacesTheWholeSetAndTheVersionOnlyIncreases() {
        ConsulConfigurationAdapter adapter = adapter();
        ServiceId id = ServiceId.of("a3-replace-service");
        adapter.publish(id, ENV, new ConfigSet(Map.of("db.host", "a", "db.port", "1", "cache.enabled", "false")));
        long first = adapter.currentVersion(id, ENV).index();
        assertTrue(first > 0);

        adapter.publish(id, ENV, new ConfigSet(Map.of("db.host", "b", "db.port", "2")));

        ResolvedConfig resolved = adapter.resolve(id, ENV);
        assertEquals(Map.of("db.host", "b", "db.port", "2"), resolved.values(),
                "remplacement complet : cache.enabled n'est plus publie");
        assertFalse(resolved.values().containsKey("cache.enabled"));
        assertTrue(adapter.currentVersion(id, ENV).index() > first, "la version ne fait qu'augmenter");
    }

    @Test
    void environmentsOfTheSameServiceAreIndependent() {
        ConsulConfigurationAdapter adapter = adapter();
        ServiceId id = ServiceId.of("a3-two-envs");
        adapter.publish(id, TargetEnvironment.of("production"), new ConfigSet(Map.of("k", "prod")));
        adapter.publish(id, TargetEnvironment.of("staging"), new ConfigSet(Map.of("k", "stg")));

        assertEquals("prod", adapter.resolve(id, TargetEnvironment.of("production")).values().get("k"));
        assertEquals("stg", adapter.resolve(id, TargetEnvironment.of("staging")).values().get("k"));
    }

    @Test
    void anUnreachableConsulFailsWithAConfigurationExceptionAndPublishesNothing() {
        ConsulConfigurationAdapter unreachable = new ConsulConfigurationAdapter(URI.create("http://127.0.0.1:1"), null);

        assertThrows(ConfigurationException.class,
                () -> unreachable.publish(ID, ENV, new ConfigSet(Map.of("k", "v"))));
        assertThrows(ConfigurationException.class, () -> unreachable.currentVersion(ID, ENV));
        assertThrows(ConfigurationException.class, () -> unreachable.resolve(ID, ENV));
    }

    @Test
    void watchIsNotifiedWhenTheConfigurationChanges() throws InterruptedException {
        ConsulConfigurationAdapter adapter = adapter();
        adapter.publish(ID, ENV, new ConfigSet(Map.of("FEATURE_FLAG_COMMENTS", "false")));

        BlockingQueue<ResolvedConfig> notifications = new ArrayBlockingQueue<>(1);
        ConfigWatch watch = adapter.watch(ID, ENV, notifications::add);

        // Laisse le watch etablir sa ligne de base (currentVersion) puis sa
        // premiere blocking query avant de publier le changement qu'il doit
        // detecter — deux allers-retours HTTP desormais (voir le correctif
        // de ConsulConfigurationAdapter.pollLoop), d'ou une marge un peu
        // plus large que pour un aller-retour unique.
        Thread.sleep(800);
        adapter.publish(ID, ENV, new ConfigSet(Map.of("FEATURE_FLAG_COMMENTS", "true")));

        ResolvedConfig changed = notifications.poll(10, TimeUnit.SECONDS);
        assertNotNull(changed, "le watch doit etre notifie du changement");
        assertEquals("true", changed.values().get("FEATURE_FLAG_COMMENTS"));

        adapter.unwatch(watch);
    }
}
