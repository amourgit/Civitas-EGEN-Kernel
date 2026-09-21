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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests de niveau 2 (voir docs/architecture/17-strategie-de-tests.md) :
 * verifient la logique d'orchestration Declare -&gt; Resolve -&gt; Compose -&gt;
 * Delegate en injectant des doubles en memoire pour DeploymentPort et
 * DiscoveryPort — sans dependre d'un vrai Nomad ni d'un vrai Consul. Chaque
 * appel a {@code engine.reconcile(id)} est fait directement et de facon
 * synchrone (le worker/thread de fond n'est jamais demarre ici).
 */
class ReconciliationEngineTest {

    private static final ServiceId SERVICE_ID = ServiceId.of("news-service");
    private static final Duration SHORT_GRACE_PERIOD = Duration.ofMillis(50);

    private InMemoryRegistryStore registryStore;
    private FakeDeploymentPort deploymentPort;
    private FakeDiscoveryPort discoveryPort;
    private WorkQueue workQueue;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        registryStore = new InMemoryRegistryStore();
        deploymentPort = new FakeDeploymentPort();
        discoveryPort = new FakeDiscoveryPort();
        workQueue = new WorkQueue();
        engine = new ReconciliationEngine(workQueue, registryStore, deploymentPort, discoveryPort);
        registryStore.save(new DesiredState(aManifest(SHORT_GRACE_PERIOD), 0L, TargetEnvironment.of("test")));
    }

    private static ServiceManifest aManifest(Duration gracePeriod) {
        return new ServiceManifest(
                SERVICE_ID,
                ServiceVersion.parse("2.4.0"),
                new ServiceRuntime(RuntimeType.CONTAINER,
                        "registry.civitas.africa/news-service:2.4.0", "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0",
                        "500m", "512Mi", new ReplicaRange(2, 6)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(gracePeriod));
    }

    private ServiceStatus currentStatus() {
        return registryStore.findStatus(SERVICE_ID).orElseThrow();
    }

    private void reconcileNTimes(int n) {
        for (int i = 0; i < n; i++) {
            engine.reconcile(SERVICE_ID);
        }
    }

    @Test
    void advancesFromDeclaredToRunningAsDeploymentAndDiscoveryBothConverge() {
        deploymentPort.setCyclesToConverge(1);

        reconcileNTimes(1); // DECLARED -> REGISTERED
        assertEquals(Phase.REGISTERED, currentStatus().phase());

        reconcileNTimes(1); // REGISTERED -> CONFIGURED
        assertEquals(Phase.CONFIGURED, currentStatus().phase());

        reconcileNTimes(1); // CONFIGURED -> DEPLOYING (Delegate: create())
        assertEquals(Phase.DEPLOYING, currentStatus().phase());
        assertEquals(1, deploymentPort.createCallCount(SERVICE_ID));

        reconcileNTimes(1); // observe : deployment ET discovery convergent -> RUNNING
        assertEquals(Phase.RUNNING, currentStatus().phase());
        assertEquals(2, discoveryPort.registeredCount(SERVICE_ID));
    }

    @Test
    void staysInDeployingUntilBothPortsReportConvergence() {
        deploymentPort.setCyclesToConverge(3);

        reconcileNTimes(3); // DECLARED -> REGISTERED -> CONFIGURED -> DEPLOYING
        assertEquals(Phase.DEPLOYING, currentStatus().phase());

        reconcileNTimes(1); // observe #1 : pas encore converge
        assertEquals(Phase.DEPLOYING, currentStatus().phase());
        reconcileNTimes(1); // observe #2 : pas encore converge
        assertEquals(Phase.DEPLOYING, currentStatus().phase());
        reconcileNTimes(1); // observe #3 : converge (deployment + discovery)
        assertEquals(Phase.RUNNING, currentStatus().phase());
    }

    @Test
    void movesToFailedAfterTooManyConsecutiveAdapterFailures() {
        deploymentPort.setAlwaysThrowOnCreate(true);

        reconcileNTimes(2); // DECLARED -> REGISTERED -> CONFIGURED (pas d'appel adapter)
        assertEquals(Phase.CONFIGURED, currentStatus().phase());

        reconcileNTimes(5); // 5 tentatives de create() consecutives en echec -> FAILED
        assertEquals(Phase.FAILED, currentStatus().phase());
    }

    @Test
    void neverAutomaticallyRetriesOnceFailed_noSilentInfiniteRetry() {
        deploymentPort.setAlwaysThrowOnCreate(true);
        reconcileNTimes(2 + 5);
        assertEquals(Phase.FAILED, currentStatus().phase());

        int callsBefore = deploymentPort.createCallCount(SERVICE_ID);
        reconcileNTimes(1);
        assertEquals(callsBefore, deploymentPort.createCallCount(SERVICE_ID));
        assertEquals(Phase.FAILED, currentStatus().phase());
    }

    @Test
    void gracefulShutdownDeregistersBeforeStoppingAndOnlyAfterTheGracePeriod()
            throws InterruptedException {
        deploymentPort.setCyclesToConverge(1);
        reconcileNTimes(4); // -> RUNNING (voir le premier test)
        assertEquals(Phase.RUNNING, currentStatus().phase());
        assertEquals(2, discoveryPort.registeredCount(SERVICE_ID));

        registryStore.saveStatus(SERVICE_ID,
                new ServiceStatus(Phase.STOPPING, currentStatus().conditions(), currentStatus().observedGeneration()));

        engine.reconcile(SERVICE_ID); // 1er passage STOPPING : deregister, PAS encore STOPPED
        assertEquals(Phase.STOPPING, currentStatus().phase());
        assertEquals(0, discoveryPort.registeredCount(SERVICE_ID)); // deregistre AVANT l'arret (09.3)

        Thread.sleep(SHORT_GRACE_PERIOD.toMillis() + 20);

        engine.reconcile(SERVICE_ID); // grace period ecoulee -> STOPPED
        assertEquals(Phase.STOPPED, currentStatus().phase());
    }
}
