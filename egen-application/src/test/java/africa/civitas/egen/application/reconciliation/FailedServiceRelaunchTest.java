package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.EnabledDeploymentAdapters;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.application.usecase.DeployServiceUseCase;
import africa.civitas.egen.application.usecase.DeployServiceUseCaseImpl;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ConditionStatus;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Point ouvert 6, option (a) (docs/architecture/19-feuille-de-route.md ; 09-cycle-de-vie.md, « Relance par
 * Declare ») : de bout en bout, avec le VRAI moteur de réconciliation, un service FAILED ou STOPPED qui reçoit un
 * nouveau Declare repart et redevient RUNNING. Sans cette relance, le moteur ignore ces deux phases pour toujours.
 */
class FailedServiceRelaunchTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final TargetEnvironment ENV = TargetEnvironment.of("test");
    private static final Duration GRACE = Duration.ofMillis(20);
    private static final int MAX_CYCLES = 20;

    private final InMemoryRegistryStore registry = new InMemoryRegistryStore();
    private final FakeDeploymentPort deployment = new FakeDeploymentPort();
    private final FakeDiscoveryPort discovery = new FakeDiscoveryPort();
    private final ReconciliationEngine engine = new ReconciliationEngine(new WorkQueue(), registry, deployment,
            discovery, new NoOpObservabilityPort());
    private final DeployServiceUseCase useCase = new DeployServiceUseCaseImpl(registry, new WorkQueue(),
            new PendingDeclareTraces(), EnabledDeploymentAdapters.of(Set.of("nomad")));

    private static ServiceManifest manifest() {
        return new ServiceManifest(NEWS, ServiceVersion.parse("2.4.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry.civitas.africa/news-service:2.4.0", "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0", "500m", "512Mi",
                        new ReplicaRange(2, 6)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(GRACE));
    }

    private Phase phase() {
        return registry.findStatus(NEWS).map(ServiceStatus::phase).orElse(null);
    }

    private void cyclesUntil(Phase target, long pauseMillis) throws InterruptedException {
        for (int i = 0; i < MAX_CYCLES && phase() != target; i++) {
            engine.reconcile(NEWS);
            if (pauseMillis > 0) {
                Thread.sleep(pauseMillis);
            }
        }
    }

    @Test
    void aFailedServiceIgnoredByTheEngineRecoversThroughANewDeclare() throws InterruptedException {
        useCase.declare(manifest(), ENV);
        cyclesUntil(Phase.RUNNING, 0);
        assertEquals(Phase.RUNNING, phase());

        // L'échec : cinq erreurs d'adapter consécutives ont mis le service en FAILED (état simulé directement).
        registry.saveStatus(NEWS, new ServiceStatus(Phase.FAILED, List.of(new Condition("DeploymentReady",
                ConditionStatus.FALSE, "AdapterFailureThresholdExceeded", "namespace introuvable", Instant.EPOCH)),
                1L));
        engine.reconcile(NEWS);
        engine.reconcile(NEWS);
        assertEquals(Phase.FAILED, phase(), "sans relance, le moteur ignore un service FAILED, à jamais");

        useCase.declare(manifest(), ENV); // l'opérateur a corrigé la cause et redéclare
        assertEquals(Phase.DECLARED, phase());
        cyclesUntil(Phase.RUNNING, 0);

        assertEquals(Phase.RUNNING, phase(), "le service doit être revenu en RUNNING");
        assertEquals(2, discovery.registeredCount(NEWS), "exactement les 2 instances voulues dans Discovery");
        ServiceStatus finalStatus = registry.findStatus(NEWS).orElseThrow();
        assertFalse(finalStatus.conditions().stream().anyMatch(c -> "AdapterFailureThresholdExceeded".equals(c.reason())),
                "l'ancien échec ne doit plus apparaître dans les Conditions : " + finalStatus.conditions());
        assertEquals(2L, finalStatus.observedGeneration());
    }

    @Test
    void aStoppedServiceIgnoredByTheEngineRunsAgainThroughANewDeclare() throws InterruptedException {
        useCase.declare(manifest(), ENV);
        cyclesUntil(Phase.RUNNING, 0);
        // L'opérateur demande l'arrêt (ce que fait le use case d'arrêt), puis le moteur l'accomplit.
        ServiceStatus running = registry.findStatus(NEWS).orElseThrow();
        registry.saveStatus(NEWS, new ServiceStatus(Phase.STOPPING, running.conditions(), running.observedGeneration()));
        cyclesUntil(Phase.STOPPED, GRACE.toMillis() + 10);
        assertEquals(Phase.STOPPED, phase());
        assertEquals(0, discovery.registeredCount(NEWS), "plus aucune instance enregistrée une fois arrêté");

        useCase.declare(manifest(), ENV);
        cyclesUntil(Phase.RUNNING, 0);

        assertEquals(Phase.RUNNING, phase(), "le service arrêté doit repartir");
        assertEquals(2, discovery.registeredCount(NEWS));
    }
}
