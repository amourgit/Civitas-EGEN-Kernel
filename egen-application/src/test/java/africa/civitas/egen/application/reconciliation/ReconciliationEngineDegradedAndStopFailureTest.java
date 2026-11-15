package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
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
import java.time.Duration;
import africa.civitas.egen.domain.lifecycle.ConditionStatus;
import africa.civitas.egen.domain.lifecycle.Condition;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Niveau 2 (docs/architecture/17) : deux decisions du 14 novembre 2026 (docs/architecture/09).
 * 4a : un service reste DEGRADED au plus {@code egen.reconciliation.degraded-timeout}, puis passe FAILED.
 * I18 : un echec de l'arret lui-meme laisse le service STOPPING, avec une Condition et un compteur plafonne a 5/5.
 */
class ReconciliationEngineDegradedAndStopFailureTest {

    private static final ServiceId SERVICE_ID = ServiceId.of("news-service");
    private static final Duration GRACE = Duration.ofMillis(50);

    private InMemoryRegistryStore registry;
    private FakeDeploymentPort deployment;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistryStore();
        deployment = new FakeDeploymentPort();
        engine = new ReconciliationEngine(new WorkQueue(), registry, deployment, new FakeDiscoveryPort(),
                new NoOpObservabilityPort());
        registry.save(new DesiredState(new ServiceManifest(SERVICE_ID, ServiceVersion.parse("2.4.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry.civitas.africa/news-service:2.4.0", "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0", "500m", "512Mi",
                        new ReplicaRange(2, 6)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(GRACE)), 0L, TargetEnvironment.of("test")));
        deployment.setCyclesToConverge(1);
        for (int i = 0; i < 4; i++) {
            engine.reconcile(SERVICE_ID);
        }
        assertEquals(Phase.RUNNING, status().phase());
    }

    private ServiceStatus status() {
        return registry.findStatus(SERVICE_ID).orElseThrow();
    }

    private Condition condition(String type) {
        return status().conditions().stream().filter(c -> c.type().equals(type)).findFirst()
                .orElseThrow(() -> new AssertionError("Condition absente : " + type));
    }

    @Test
    void aServiceDegradedLongerThanTheTimeoutBecomesFailedWithTheReasonDegradedTimeout()
            throws InterruptedException {
        engine.degradedTimeout(Duration.ofMillis(50));
        deployment.setHealthyOverride(1);
        engine.reconcile(SERVICE_ID);
        assertEquals(Phase.DEGRADED, status().phase());

        Thread.sleep(80);
        engine.reconcile(SERVICE_ID);

        assertEquals(Phase.FAILED, status().phase());
        assertEquals("DegradedTimeout", condition("DeploymentReady").reason());
        assertTrue(condition("DeploymentReady").message().startsWith("DEGRADED depuis plus de PT0.05S"),
                condition("DeploymentReady").message());
    }

    @Test
    void aServiceDegradedForLessThanTheTimeoutStaysDegraded() {
        deployment.setHealthyOverride(1); // delai par defaut : 10 minutes
        for (int i = 0; i < 3; i++) {
            engine.reconcile(SERVICE_ID);
        }
        assertEquals(Phase.DEGRADED, status().phase());
    }

    @Test
    void theDegradedTimerRestartsWhenTheServiceRecoversInBetween() throws InterruptedException {
        engine.degradedTimeout(Duration.ofMillis(200));
        deployment.setHealthyOverride(1);
        engine.reconcile(SERVICE_ID);
        assertEquals(Phase.DEGRADED, status().phase());
        Thread.sleep(150);

        deployment.setHealthyOverride(null);
        engine.reconcile(SERVICE_ID);
        assertEquals(Phase.RUNNING, status().phase());

        deployment.setHealthyOverride(1);
        engine.reconcile(SERVICE_ID);
        Thread.sleep(100); // 250 ms depuis la premiere degradation, 100 ms depuis la seconde
        engine.reconcile(SERVICE_ID);

        assertEquals(Phase.DEGRADED, status().phase());
    }

    @Test
    void aZeroTimeoutDisablesTheRule() throws InterruptedException {
        engine.degradedTimeout(Duration.ZERO);
        deployment.setHealthyOverride(1);
        engine.reconcile(SERVICE_ID);
        Thread.sleep(60);
        engine.reconcile(SERVICE_ID);
        assertEquals(Phase.DEGRADED, status().phase());
    }

    @Test
    void aNegativeOrMissingTimeoutIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> engine.degradedTimeout(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> engine.degradedTimeout(null));
    }

    @Test
    void aFailingStopLeavesTheServiceStoppingWithAConditionAndACounterCappedAtFive() throws InterruptedException {
        registry.saveStatus(SERVICE_ID, new ServiceStatus(Phase.STOPPING, status().conditions(),
                status().observedGeneration()));
        deployment.setAlwaysThrowOnStop(true);
        engine.reconcile(SERVICE_ID);            // dé-enregistrement reussi, debut du delai de grace
        Thread.sleep(GRACE.toMillis() + 20);

        for (int attempt = 1; attempt <= 7; attempt++) {
            engine.reconcile(SERVICE_ID);
            assertEquals(Phase.STOPPING, status().phase(), "tentative " + attempt);
            assertEquals("StopFailed", condition("DeploymentStopped").reason());
            String expected = "tentative " + Math.min(attempt, 5) + "/5 : ";
            assertTrue(condition("DeploymentStopped").message().startsWith(expected),
                    condition("DeploymentStopped").message());
            assertTrue(condition("DeploymentStopped").message().contains("panne simulee de l'arret"));
        }

        deployment.setAlwaysThrowOnStop(false);
        engine.reconcile(SERVICE_ID);
        assertEquals(Phase.STOPPED, status().phase());
    }

    /** Ecart I16 : un statut FAILED garde les Conditions posees avant l'echec (docs/architecture/09). */
    @Test
    void aServiceFailedByTheDeploymentEngineKeepsAllTheConditionsKnownBeforeTheFailure() {
        ServiceId other = ServiceId.of("other-service");
        registry.save(new DesiredState(new ServiceManifest(other, ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry.civitas.africa/other-service:1.0.0", "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/other-service:1.0.0", "500m", "512Mi",
                        new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(GRACE)), 0L, TargetEnvironment.of("test")));
        deployment.setAlwaysThrowOnCreate(true);
        for (int i = 0; i < 12 && registry.findStatus(other).map(ServiceStatus::phase).orElse(null) != Phase.FAILED; i++) {
            engine.reconcile(other);
        }

        ServiceStatus failed = registry.findStatus(other).orElseThrow();
        assertEquals(Phase.FAILED, failed.phase());
        java.util.Set<String> types = new java.util.TreeSet<>();
        failed.conditions().forEach(c -> types.add(c.type()));
        assertEquals(java.util.Set.of("ManifestValid", "ConfigResolved", "DependenciesSatisfied",
                "MessagingBindingsReady", "DeploymentReady"), types);
    }
}
