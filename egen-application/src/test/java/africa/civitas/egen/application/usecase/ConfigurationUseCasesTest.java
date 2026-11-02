package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.ConfigChangeHandler;
import africa.civitas.egen.application.port.ConfigSet;
import africa.civitas.egen.application.port.ConfigVersion;
import africa.civitas.egen.application.port.ConfigWatch;
import africa.civitas.egen.application.port.ConfigurationException;
import africa.civitas.egen.application.port.ConfigurationPort;
import africa.civitas.egen.application.port.ResolvedConfig;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.ConfigurationSpec;
import africa.civitas.egen.domain.model.ConfigurationSpec.Rule;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 2 (docs 17). Contrat : 13, « Publication de configuration » (A3) — validation exhaustive,
 * remplacement complet, effet sur la reconciliation, store indisponible.
 */
class ConfigurationUseCasesTest {

    private static final ServiceId ID = ServiceId.of("news-service");
    private static final TargetEnvironment ENV = TargetEnvironment.of("production");

    /** Double minimal d'un store cle/valeur versionne par couple (service, environnement). */
    private static final class FakeConfigurationPort implements ConfigurationPort {
        private final Map<String, Map<String, String>> stored = new HashMap<>();
        private final Map<String, Long> versions = new HashMap<>();
        boolean failing;
        int publishCalls;

        private static String key(ServiceId id, TargetEnvironment env) {
            return env.name() + "/" + id.value();
        }

        @Override
        public ResolvedConfig resolve(ServiceId id, TargetEnvironment environment) {
            check();
            return new ResolvedConfig(stored.getOrDefault(key(id, environment), Map.of()),
                    new ConfigVersion(versions.getOrDefault(key(id, environment), 0L)));
        }

        @Override
        public void publish(ServiceId id, TargetEnvironment environment, ConfigSet values) {
            check();
            publishCalls++;
            stored.put(key(id, environment), Map.copyOf(values.values())); // remplacement complet
            versions.merge(key(id, environment), 1L, Long::sum);
        }

        @Override
        public ConfigWatch watch(ServiceId id, TargetEnvironment environment, ConfigChangeHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unwatch(ConfigWatch watch) {
        }

        @Override
        public ConfigVersion currentVersion(ServiceId id, TargetEnvironment environment) {
            check();
            return new ConfigVersion(versions.getOrDefault(key(id, environment), 0L));
        }

        private void check() {
            if (failing) {
                throw new ConfigurationException("Consul KV injoignable");
            }
        }
    }

    private InMemoryRegistryStore registry;
    private FakeConfigurationPort configuration;
    private WorkQueue workQueue;
    private PublishConfigurationUseCase publisher;
    private ReadConfigurationUseCase reader;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistryStore();
        configuration = new FakeConfigurationPort();
        workQueue = new WorkQueue();
        publisher = new PublishConfigurationUseCaseImpl(registry, configuration, workQueue);
        reader = new ReadConfigurationUseCaseImpl(registry, configuration);
    }

    private void declare(Phase phase) {
        registry.save(new DesiredState(manifest(), 0L, ENV));
        registry.saveStatus(ID, new ServiceStatus(phase, List.of(), 1L));
    }

    // ------------------------------------------------------------------ publication

    @Test
    void anUndeclaredServiceIsNotFoundAndNothingIsWritten() {
        assertThrows(ServiceNotFoundException.class, () -> publisher.publish(ID, ENV, Map.of("db.host", "h")));
        assertEquals(0, configuration.publishCalls);
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void aValidPublicationIsStoredReturnsItsVersionAndEnqueuesTheService() throws InterruptedException {
        declare(Phase.REGISTERED);

        PublishedConfiguration published = publisher.publish(ID, ENV, Map.of("db.host", "pg", "db.port", "5432"));

        assertEquals(new PublishedConfiguration(ID, ENV, 1L), published);
        assertEquals(Map.of("db.host", "pg", "db.port", "5432"), reader.read(ID, ENV).values());
        assertEquals(ID, workQueue.take()); // la reconciliation releve ConfigResolved
    }

    @Test
    void everyViolationIsReportedTogetherAndNothingIsWritten() {
        declare(Phase.REGISTERED);

        InvalidConfigurationException error = assertThrows(InvalidConfigurationException.class, () ->
                publisher.publish(ID, ENV, Map.of("db.port", "VALEUR-SECRETE-1", "db.password", "VALEUR-SECRETE-2",
                        "inconnue", "x")));

        assertEquals(List.of(
                        "db.host:" + Rule.MISSING_REQUIRED_KEY,
                        "db.password:" + Rule.SECRET_NOT_PUBLISHABLE,
                        "db.port:" + Rule.TYPE_MISMATCH,
                        "inconnue:" + Rule.UNKNOWN_KEY),
                error.violations().stream().map(v -> v.key() + ":" + v.rule()).toList());
        assertFalse(error.getMessage().contains("VALEUR-SECRETE"), "jamais une valeur : " + error.getMessage());
        assertEquals(0, configuration.publishCalls, "rien n'est publie quand la validation echoue");
    }

    @Test
    void aSecondPublicationReplacesTheWholeSetAndBumpsTheVersion() {
        declare(Phase.RUNNING);
        publisher.publish(ID, ENV, Map.of("db.host", "a", "db.port", "1", "cache.enabled", "false"));

        PublishedConfiguration second = publisher.publish(ID, ENV, Map.of("db.host", "b", "db.port", "2"));

        assertEquals(2L, second.version());
        assertEquals(Map.of("db.host", "b", "db.port", "2"), reader.read(ID, ENV).values(),
                "remplacement complet : cache.enabled n'est plus publie, son defaut du manifeste s'applique");
    }

    @Test
    void configurationsAreIndependentPerEnvironment() {
        declare(Phase.RUNNING);
        TargetEnvironment staging = TargetEnvironment.of("staging");

        publisher.publish(ID, ENV, Map.of("db.host", "prod", "db.port", "1"));
        publisher.publish(ID, staging, Map.of("db.host", "stg", "db.port", "2"));

        assertEquals("prod", reader.read(ID, ENV).values().get("db.host"));
        assertEquals("stg", reader.read(ID, staging).values().get("db.host"));
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void aStoppedOrRemovedServiceKeepsItsConfigurationWithoutBeingEnqueued() throws InterruptedException {
        for (Phase phase : List.of(Phase.STOPPING, Phase.STOPPED, Phase.REMOVING, Phase.REMOVED)) {
            declare(phase);

            publisher.publish(ID, ENV, Map.of("db.host", "h", "db.port", "1"));
        }

        assertEquals(4, configuration.publishCalls, "la configuration est conservee");
        workQueue.enqueue(ServiceId.of("sentinelle"));
        assertEquals(ServiceId.of("sentinelle"), workQueue.take(), "aucun enfilage pour un service arrete ou en retrait");
    }

    @Test
    void anUnavailableStoreFailsWithoutPublishingAnythingNorEnqueuing() {
        declare(Phase.REGISTERED);
        configuration.failing = true;

        assertThrows(ConfigurationStoreUnavailableException.class,
                () -> publisher.publish(ID, ENV, Map.of("db.host", "h", "db.port", "1")));
        assertEquals(0, configuration.publishCalls);
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void aNeverPublishedConfigurationIsEmptyWithVersionZero() {
        declare(Phase.REGISTERED);

        ServiceConfiguration read = reader.read(ID, ENV);

        assertTrue(read.values().isEmpty());
        assertEquals(0L, read.version());
    }

    @Test
    void readingAnUndeclaredServiceIsNotFoundButAnUnpublishedOneIsNot() {
        assertThrows(ServiceNotFoundException.class, () -> reader.read(ID, ENV));
        declare(Phase.DECLARED);
        assertEquals(0L, reader.read(ID, ENV).version());
    }

    @Test
    void readingFromAnUnavailableStoreFails() {
        declare(Phase.REGISTERED);
        configuration.failing = true;

        assertThrows(ConfigurationStoreUnavailableException.class, () -> reader.read(ID, ENV));
    }

    private static ServiceManifest manifest() {
        ConfigurationSpec configuration = new ConfigurationSpec(
                List.of(new ConfigurationSpec.Key("db.host", "string", null),
                        new ConfigurationSpec.Key("db.port", "integer", null),
                        new ConfigurationSpec.Key("cache.enabled", "boolean", "true")),
                List.of(new ConfigurationSpec.SecretReferenceSpec("db.password", "vault", "secret/news/db")));
        return new ServiceManifest(ID, ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry.civitas.africa/news:1.0.0", "java"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news:1.0.0", "500m", "512Mi",
                        new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(Duration.ofMillis(50)), List.of(), null, null, null, configuration, null);
    }
}
