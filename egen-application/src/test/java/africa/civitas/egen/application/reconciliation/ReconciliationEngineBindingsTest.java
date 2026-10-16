package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.BindingStatus;
import africa.civitas.egen.application.port.ConfigChangeHandler;
import africa.civitas.egen.application.port.ConfigSet;
import africa.civitas.egen.application.port.ConfigVersion;
import africa.civitas.egen.application.port.ConfigWatch;
import africa.civitas.egen.application.port.ConfigurationException;
import africa.civitas.egen.application.port.ConfigurationPort;
import africa.civitas.egen.application.port.EventHandler;
import africa.civitas.egen.application.port.MessagingException;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.ResolvedConfig;
import africa.civitas.egen.application.port.SecretRef;
import africa.civitas.egen.application.port.SecretReference;
import africa.civitas.egen.application.port.SecretsException;
import africa.civitas.egen.application.port.SecretsPort;
import africa.civitas.egen.application.port.Subscription;
import africa.civitas.egen.application.port.TopicSpec;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.event.TechnicalEvent;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ConditionStatus;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.ConfigurationSpec;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.EventSpec;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.OwnerTeam;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conditions {@code ConfigResolved} et {@code MessagingBindingsReady}
 * (docs/architecture/09-cycle-de-vie.md et 04-moteur-de-reconciliation.md) :
 * le moteur contre des doubles de ports, sans aucune I/O.
 */
class ReconciliationEngineBindingsTest {

    private static final ServiceId SERVICE_ID = ServiceId.of("news-service");

    private static final class FakeSecrets implements SecretsPort {
        final Set<String> existing = new HashSet<>();
        final List<String> resolved = new ArrayList<>();

        @Override
        public SecretRef resolve(SecretReference reference) {
            resolved.add(reference.path());
            if (!existing.contains(reference.path())) {
                throw new SecretsException("Aucun secret Vault a l'emplacement \"" + reference.path() + "\"");
            }
            return new SecretRef(reference.path(), reference.provider());
        }

        @Override
        public void rotate(SecretReference reference) {
        }
    }

    private static final class FakeConfiguration implements ConfigurationPort {
        final Map<String, String> values = new HashMap<>();
        boolean failing;
        int resolveCalls;

        @Override
        public ResolvedConfig resolve(ServiceId id, TargetEnvironment environment) {
            resolveCalls++;
            if (failing) {
                throw new ConfigurationException("Consul KV injoignable");
            }
            return new ResolvedConfig(values, new ConfigVersion(1));
        }

        @Override
        public void publish(ServiceId id, TargetEnvironment environment, ConfigSet values) {
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
            return new ConfigVersion(1);
        }
    }

    private static final class FakeMessaging implements MessagingPort {
        final List<TopicSpec> created = new ArrayList<>();
        boolean failing;

        @Override
        public void createTopicOrStream(TopicSpec spec) {
            if (failing) {
                throw new MessagingException("NATS injoignable");
            }
            created.add(spec);
        }

        @Override
        public void publish(TechnicalEvent event, String subjectOrTopic) {
        }

        @Override
        public Subscription subscribe(String subjectOrTopic, String consumerGroup, EventHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unsubscribe(Subscription subscription) {
        }

        @Override
        public BindingStatus getBindingStatus(String subjectOrTopic, String consumerGroup) {
            throw new UnsupportedOperationException();
        }
    }

    private InMemoryRegistryStore registry;
    private FakeDeploymentPort deployment;
    private FakeDiscoveryPort discovery;
    private FakeSecrets secrets;
    private FakeConfiguration configuration;
    private FakeMessaging messaging;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistryStore();
        deployment = new FakeDeploymentPort();
        discovery = new FakeDiscoveryPort();
        secrets = new FakeSecrets();
        configuration = new FakeConfiguration();
        messaging = new FakeMessaging();
        engine = new ReconciliationEngine(new WorkQueue(), registry, deployment, discovery,
                new NoOpObservabilityPort(), new PendingDeclareTraces(),
                ServiceBindings.of(secrets, configuration, messaging));
    }

    private void declare(EventSpec events, ConfigurationSpec spec) {
        ServiceManifest manifest = new ServiceManifest(SERVICE_ID, ServiceVersion.parse("2.4.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry/news:2.4.0", "java"),
                new DeploymentSpec("nomad", "registry/news:2.4.0", "500m", "512Mi", new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(Duration.ofMillis(50)), List.of(), OwnerTeam.unassigned(), null,
                events, spec, null);
        registry.save(new DesiredState(manifest, 0L, TargetEnvironment.of("test")));
    }

    private void reconcile(int times) {
        for (int i = 0; i < times; i++) {
            engine.reconcile(SERVICE_ID);
        }
    }

    private ServiceStatus status() {
        return registry.findStatus(SERVICE_ID).orElseThrow();
    }

    private Condition condition(String type) {
        return status().conditions().stream().filter(c -> c.type().equals(type)).findFirst().orElseThrow(
                () -> new AssertionError("Condition absente : " + type + " dans " + status().conditions()));
    }

    private static ConfigurationSpec configWithSecret() {
        return new ConfigurationSpec(List.of(),
                List.of(new ConfigurationSpec.SecretReferenceSpec("DB_PASSWORD", "vault", "secret/data/news-service/db")));
    }

    @Test
    void theManifestValidConditionIsEmittedWhenTheServiceIsRegistered() {
        declare(null, null);

        reconcile(1); // DECLARED -> REGISTERED

        assertEquals(Phase.REGISTERED, status().phase());
        assertEquals(ConditionStatus.TRUE, condition("ManifestValid").status());
    }

    // ------------------------------------------------------------------ ConfigResolved

    @Test
    void aManifestWithoutConfigurationPassesThroughWithATrueCondition() {
        declare(null, null);

        reconcile(2); // DECLARED -> REGISTERED -> CONFIGURED

        assertEquals(Phase.CONFIGURED, status().phase());
        assertEquals(ConditionStatus.TRUE, condition("ConfigResolved").status());
        assertEquals("NothingToResolve", condition("ConfigResolved").reason());
        assertEquals(0, configuration.resolveCalls, "aucune cle requise : le store de configuration n'est jamais interroge");
        assertTrue(secrets.resolved.isEmpty());
    }

    @Test
    void anUnresolvedSecretKeepsTheServiceRegisteredAndTheConditionSaysWhy() {
        declare(null, configWithSecret());

        reconcile(5);

        assertEquals(Phase.REGISTERED, status().phase(), "REGISTERED -> CONFIGURED exige la resolution des secrets");
        Condition condition = condition("ConfigResolved");
        assertEquals(ConditionStatus.FALSE, condition.status());
        assertEquals("SecretUnresolved", condition.reason());
        assertTrue(condition.message().contains("DB_PASSWORD"));
        assertTrue(condition.message().contains("secret/data/news-service/db"));
        assertEquals(0, deployment.createCallCount(SERVICE_ID), "rien n'est deploye tant que la configuration n'est pas resolue");
    }

    @Test
    void theServiceMovesOnAsSoonAsTheSecretExists_noOperatorActionOnTheService() {
        declare(null, configWithSecret());
        reconcile(3);
        assertEquals(Phase.REGISTERED, status().phase());

        secrets.existing.add("secret/data/news-service/db");
        reconcile(1);

        assertEquals(Phase.CONFIGURED, status().phase());
        assertEquals(ConditionStatus.TRUE, condition("ConfigResolved").status());
        assertEquals("Resolved", condition("ConfigResolved").reason());
    }

    @Test
    void aRequiredKeyWithoutValueNorDefaultBlocksAndIsNamed() {
        declare(null, new ConfigurationSpec(List.of(new ConfigurationSpec.Key("PAGE_SIZE", "integer", null),
                new ConfigurationSpec.Key("FEATURE_FLAG_COMMENTS", "boolean", "false")), List.of()));

        reconcile(4);

        assertEquals(Phase.REGISTERED, status().phase());
        assertEquals("RequiredKeyMissing", condition("ConfigResolved").reason());
        assertTrue(condition("ConfigResolved").message().contains("PAGE_SIZE"));
        assertFalse(condition("ConfigResolved").message().contains("FEATURE_FLAG_COMMENTS"),
                "une cle qui a un defaut n'est pas manquante");

        configuration.values.put("PAGE_SIZE", "20");
        reconcile(1);

        assertEquals(Phase.CONFIGURED, status().phase());
    }

    @Test
    void aDefaultSatisfiesARequiredKey() {
        declare(null, new ConfigurationSpec(
                List.of(new ConfigurationSpec.Key("FEATURE_FLAG_COMMENTS", "boolean", "false")), List.of()));

        reconcile(2);

        assertEquals(Phase.CONFIGURED, status().phase());
    }

    @Test
    void aValueOfTheWrongTypeBlocksAndTheMessageNeverEchoesTheValue() {
        declare(null, new ConfigurationSpec(List.of(new ConfigurationSpec.Key("PAGE_SIZE", "integer", "10")), List.of()));
        configuration.values.put("PAGE_SIZE", "valeur-confidentielle-123");

        reconcile(4);

        assertEquals(Phase.REGISTERED, status().phase());
        Condition condition = condition("ConfigResolved");
        assertEquals("InvalidConfigurationValue", condition.reason());
        assertTrue(condition.message().contains("PAGE_SIZE"));
        assertTrue(condition.message().contains("integer"));
        assertFalse(condition.message().contains("valeur-confidentielle-123"));
    }

    @Test
    void anUnavailableConfigurationStoreIsAConditionNotAFailure() {
        declare(null, new ConfigurationSpec(List.of(new ConfigurationSpec.Key("PAGE_SIZE", "integer", "10")), List.of()));
        configuration.failing = true;

        reconcile(8);

        assertEquals(Phase.REGISTERED, status().phase(), "un store de configuration en panne ne fait jamais passer le service en FAILED");
        assertEquals("ConfigurationUnavailable", condition("ConfigResolved").reason());

        configuration.failing = false;
        reconcile(1);
        assertEquals(Phase.CONFIGURED, status().phase());
    }

    // ------------------------------------------------------------------ MessagingBindingsReady

    private static EventSpec events() {
        return new EventSpec(
                List.of(new EventSpec.Published("africa.civitas.news.article.created.v1", null)),
                List.of(new EventSpec.Consumed("africa.civitas.notification.delivery-failed.v1", "news-consumers", null)));
    }

    @Test
    void streamsAreEnsuredOnePerEventTypePublishedOrConsumedBeforeTheDeploymentIsDelegated() {
        declare(events(), null);

        reconcile(3); // DECLARED -> REGISTERED -> CONFIGURED -> DEPLOYING

        assertEquals(Phase.DEPLOYING, status().phase());
        assertEquals(List.of(
                new TopicSpec("EGEN-africa~civitas~news~article~created~v1", "africa.civitas.news.article.created.v1"),
                new TopicSpec("EGEN-africa~civitas~notification~delivery-failed~v1",
                        "africa.civitas.notification.delivery-failed.v1")), messaging.created);
        assertEquals(ConditionStatus.TRUE, condition("MessagingBindingsReady").status());
        assertEquals("StreamsEnsured", condition("MessagingBindingsReady").reason());
        assertEquals(1, deployment.createCallCount(SERVICE_ID));
    }

    @Test
    void aManifestWithoutEventsGetsATrueConditionAndCreatesNoStream() {
        declare(null, null);

        reconcile(3);

        assertEquals("NoEventsDeclared", condition("MessagingBindingsReady").reason());
        assertTrue(messaging.created.isEmpty());
    }

    @Test
    void anUnavailableBrokerHoldsTheServiceInConfiguredWithoutDeployingAndRecovers() {
        declare(events(), null);
        messaging.failing = true;

        reconcile(6);

        assertEquals(Phase.CONFIGURED, status().phase());
        assertEquals(ConditionStatus.FALSE, condition("MessagingBindingsReady").status());
        assertEquals("MessagingUnavailable", condition("MessagingBindingsReady").reason());
        assertEquals(0, deployment.createCallCount(SERVICE_ID), "pas de deploiement sans les flux du plan d'execution");

        messaging.failing = false;
        reconcile(1);

        assertEquals(Phase.DEPLOYING, status().phase());
        assertEquals(ConditionStatus.TRUE, condition("MessagingBindingsReady").status());
    }

    @Test
    void streamsAreReEnsuredEveryCycleAndABrokerOutageNeverDegradesARunningService() {
        declare(events(), null);
        deployment.setCyclesToConverge(1);
        reconcile(4);
        assertEquals(Phase.RUNNING, status().phase());
        int createdBefore = messaging.created.size();

        reconcile(1);
        assertEquals(createdBefore + 2, messaging.created.size(), "level-triggered : re-garantis a chaque cycle, idempotent");

        messaging.failing = true;
        reconcile(2);

        assertEquals(Phase.RUNNING, status().phase());
        assertEquals(ConditionStatus.FALSE, condition("MessagingBindingsReady").status());
    }

    // ------------------------------------------------------------------ sans ports branches

    @Test
    void withoutWiredPortsAManifestThatNeedsThemGetsAnExplicitFalseCondition() {
        ReconciliationEngine unwired = new ReconciliationEngine(new WorkQueue(), registry, deployment, discovery,
                new NoOpObservabilityPort());
        declare(null, configWithSecret());

        unwired.reconcile(SERVICE_ID);
        unwired.reconcile(SERVICE_ID);

        assertEquals(Phase.REGISTERED, status().phase());
        assertEquals("PortNotConfigured", condition("ConfigResolved").reason());
    }

    @Test
    void streamNamesAreDeterministicAndInjective() {
        assertEquals("EGEN-africa~civitas~news~article~created~v1",
                ServiceBindings.streamName("africa.civitas.news.article.created.v1"));
        // '.' et '_' sont tous deux permis dans un type : l'encodage ne doit pas les confondre.
        assertFalse(ServiceBindings.streamName("a.b").equals(ServiceBindings.streamName("a_b")));
        assertFalse(ServiceBindings.streamName("a.b").contains("."));
    }
}
