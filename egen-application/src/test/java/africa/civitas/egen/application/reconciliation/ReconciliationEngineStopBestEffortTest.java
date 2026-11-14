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

/**
 * Niveau 2 (docs/architecture/17) : deux ecarts du document 19, decides le 14 novembre 2026.
 * E4 : un arret dont le deregistrement Discovery echoue est retente (delai croissant), puis continue « au mieux »
 * apres cinq echecs, avec une Condition qui le dit (docs/architecture/09, arret gracieux).
 * E3 : l'espacement des tentatives de resolution de configuration est croissant (docs/architecture/09).
 */
class ReconciliationEngineStopBestEffortTest {

    private static final ServiceId SERVICE_ID = ServiceId.of("news-service");
    private static final Duration GRACE = Duration.ofMillis(50);

    private InMemoryRegistryStore registry;
    private FakeDeploymentPort deployment;
    private FakeDiscoveryPort discovery;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistryStore();
        deployment = new FakeDeploymentPort();
        discovery = new FakeDiscoveryPort();
        engine = new ReconciliationEngine(new WorkQueue(), registry, deployment, discovery,
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
        registry.saveStatus(SERVICE_ID,
                new ServiceStatus(Phase.STOPPING, status().conditions(), status().observedGeneration()));
    }

    private ServiceStatus status() {
        return registry.findStatus(SERVICE_ID).orElseThrow();
    }

    private Condition deregistration() {
        return status().conditions().stream().filter(c -> c.type().equals("DiscoveryDeregistered")).findFirst()
                .orElseThrow(() -> new AssertionError("Condition absente : " + status().conditions()));
    }

    @Test
    void aFailingDeregistrationIsRetriedAndTheConditionSaysWhyAndWhichAttempt() {
        discovery.setAlwaysThrowOnDeregister(true);
        for (int attempt = 1; attempt <= 4; attempt++) {
            engine.reconcile(SERVICE_ID);
            assertEquals(Phase.STOPPING, status().phase());
            assertEquals("DeregistrationRetrying", deregistration().reason());
            assertEquals(ConditionStatus.FALSE, deregistration().status());
            assertTrue(deregistration().message().startsWith("tentative " + attempt + "/5 : "),
                    deregistration().message());
            assertTrue(deregistration().message().contains("Consul injoignable"), deregistration().message());
        }
    }

    @Test
    void afterFiveFailuresTheStopContinuesBestEffortAndEndsStoppedWithTheConditionKept()
            throws InterruptedException {
        discovery.setAlwaysThrowOnDeregister(true);
        for (int attempt = 1; attempt <= 5; attempt++) {
            engine.reconcile(SERVICE_ID);
        }
        assertEquals(Phase.STOPPING, status().phase()); // la periode de grace s'applique encore
        assertEquals("DeregistrationIncomplete", deregistration().reason());
        assertTrue(deregistration().message().startsWith("apres 5 echecs"), deregistration().message());

        Thread.sleep(GRACE.toMillis() + 20);
        engine.reconcile(SERVICE_ID);

        assertEquals(Phase.STOPPED, status().phase());
        assertEquals("DeregistrationIncomplete", deregistration().reason());
    }

    @Test
    void aDeregistrationThatRecoversEndsStoppedWithATrueCondition() throws InterruptedException {
        discovery.setAlwaysThrowOnDeregister(true);
        engine.reconcile(SERVICE_ID);
        engine.reconcile(SERVICE_ID);
        discovery.setAlwaysThrowOnDeregister(false);
        engine.reconcile(SERVICE_ID);
        assertEquals(ConditionStatus.TRUE, deregistration().status());
        assertEquals("Deregistered", deregistration().reason());
        assertTrue(deregistration().message().contains("2 echec"), deregistration().message());

        Thread.sleep(GRACE.toMillis() + 20);
        engine.reconcile(SERVICE_ID);
        assertEquals(Phase.STOPPED, status().phase());
    }

    @Test
    void theDelayBetweenAttemptsGrowsFromTwoToSixtySecondsAndStaysThere() {
        assertEquals(Duration.ofSeconds(2), ReconciliationEngine.growingDelay(1));
        assertEquals(Duration.ofSeconds(4), ReconciliationEngine.growingDelay(2));
        assertEquals(Duration.ofSeconds(8), ReconciliationEngine.growingDelay(3));
        assertEquals(Duration.ofSeconds(16), ReconciliationEngine.growingDelay(4));
        assertEquals(Duration.ofSeconds(32), ReconciliationEngine.growingDelay(5));
        assertEquals(Duration.ofSeconds(60), ReconciliationEngine.growingDelay(6));
        assertEquals(Duration.ofSeconds(60), ReconciliationEngine.growingDelay(500));
    }
}
