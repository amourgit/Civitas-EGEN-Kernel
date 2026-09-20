package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests de niveau 2 (voir docs/architecture/17-strategie-de-tests.md) :
 * verifient la logique d'orchestration Declare -&gt; Resolve -&gt; Compose -&gt;
 * Delegate en injectant un {@link FakeDeploymentPort} en memoire — sans
 * dependre d'un vrai Nomad. Chaque appel a {@code engine.reconcile(id)} est
 * fait directement et de facon synchrone (le worker/thread de fond n'est
 * jamais demarre ici) pour garder les tests deterministes.
 */
class ReconciliationEngineTest {

    private static final ServiceId SERVICE_ID = ServiceId.of("news-service");

    private InMemoryRegistryStore registryStore;
    private FakeDeploymentPort deploymentPort;
    private WorkQueue workQueue;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        registryStore = new InMemoryRegistryStore();
        deploymentPort = new FakeDeploymentPort();
        workQueue = new WorkQueue();
        engine = new ReconciliationEngine(workQueue, registryStore, deploymentPort);
        registryStore.save(new DesiredState(aManifest(), 0L, TargetEnvironment.of("test")));
    }

    private static ServiceManifest aManifest() {
        return new ServiceManifest(
                SERVICE_ID,
                ServiceVersion.parse("2.4.0"),
                new ServiceRuntime(RuntimeType.CONTAINER,
                        "registry.civitas.africa/news-service:2.4.0", "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0",
                        "500m", "512Mi", new ReplicaRange(2, 6)));
    }

    private ServiceStatus currentStatus() {
        return registryStore.findStatus(SERVICE_ID).orElseThrow();
    }

    @Test
    void advancesFromDeclaredToRunningAsTheServiceConverges() {
        deploymentPort.setCyclesToConverge(1);

        engine.reconcile(SERVICE_ID); // DECLARED -> REGISTERED
        assertEquals(Phase.REGISTERED, currentStatus().phase());

        engine.reconcile(SERVICE_ID); // REGISTERED -> DEPLOYING (Delegate: create())
        assertEquals(Phase.DEPLOYING, currentStatus().phase());
        assertEquals(1, deploymentPort.createCallCount(SERVICE_ID));

        engine.reconcile(SERVICE_ID); // DEPLOYING -> RUNNING (Observe : converge des le 1er appel)
        assertEquals(Phase.RUNNING, currentStatus().phase());
    }

    @Test
    void staysInDeployingUntilTheDeploymentPortReportsConvergence() {
        deploymentPort.setCyclesToConverge(3);

        engine.reconcile(SERVICE_ID); // DECLARED -> REGISTERED
        engine.reconcile(SERVICE_ID); // REGISTERED -> DEPLOYING
        engine.reconcile(SERVICE_ID); // observe #1 : pas encore converge
        assertEquals(Phase.DEPLOYING, currentStatus().phase());
        engine.reconcile(SERVICE_ID); // observe #2 : pas encore converge
        assertEquals(Phase.DEPLOYING, currentStatus().phase());
        engine.reconcile(SERVICE_ID); // observe #3 : converge
        assertEquals(Phase.RUNNING, currentStatus().phase());
    }

    @Test
    void createIsCalledAgainOnEveryPassWhileConverging_becauseItMustBeIdempotent() {
        deploymentPort.setCyclesToConverge(2);

        engine.reconcile(SERVICE_ID); // DECLARED -> REGISTERED
        engine.reconcile(SERVICE_ID); // REGISTERED -> DEPLOYING : create() #1
        engine.reconcile(SERVICE_ID); // observe #1, toujours DEPLOYING : create() #2 (level-triggered)
        engine.reconcile(SERVICE_ID); // observe #2 : converge -> RUNNING : create() #3

        assertEquals(3, deploymentPort.createCallCount(SERVICE_ID));
        assertEquals(Phase.RUNNING, currentStatus().phase());
    }

    @Test
    void movesToFailedAfterTooManyConsecutiveAdapterFailures() {
        deploymentPort.setAlwaysThrowOnCreate(true);

        engine.reconcile(SERVICE_ID); // DECLARED -> REGISTERED (pas d'appel adapter ici)
        assertEquals(Phase.REGISTERED, currentStatus().phase());

        // 5 tentatives consecutives en echec (MAX_CONSECUTIVE_FAILURES) avant FAILED
        for (int i = 0; i < 5; i++) {
            engine.reconcile(SERVICE_ID);
        }

        assertEquals(Phase.FAILED, currentStatus().phase());
    }

    @Test
    void neverAutomaticallyRetriesOnceFailed_noSilentInfiniteRetry() {
        deploymentPort.setAlwaysThrowOnCreate(true);
        engine.reconcile(SERVICE_ID);
        for (int i = 0; i < 5; i++) {
            engine.reconcile(SERVICE_ID);
        }
        assertEquals(Phase.FAILED, currentStatus().phase());

        int callsBefore = deploymentPort.createCallCount(SERVICE_ID);
        engine.reconcile(SERVICE_ID); // ne doit plus rien declencher automatiquement
        assertEquals(callsBefore, deploymentPort.createCallCount(SERVICE_ID));
        assertEquals(Phase.FAILED, currentStatus().phase());
    }
}
