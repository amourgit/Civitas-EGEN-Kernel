package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ConditionStatus;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.Dependency;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 2 (docs 17). Contrat : 06 (« Validation du manifeste », point 4), 09 (exemple de
 * Condition {@code VersionConstraintUnmet}), 10 et 20 (etape 2 : « les deux sont RUNNING en
 * versions compatibles »). Un manifeste dont la dependance est dans une version incompatible est
 * accepte, mais le service est retenu a {@code CONFIGURED} avec une Condition explicite — jamais un
 * rejet silencieux, jamais un deploiement qui ignore la contrainte.
 */
class ReconciliationEngineDependencyVersionTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final ServiceId NOTIFICATION = ServiceId.of("notification-service");

    private InMemoryRegistryStore registryStore;
    private FakeDeploymentPort deploymentPort;
    private ReconciliationEngine engine;

    @BeforeEach
    void setUp() {
        registryStore = new InMemoryRegistryStore();
        deploymentPort = new FakeDeploymentPort();
        engine = new ReconciliationEngine(new WorkQueue(), registryStore, deploymentPort,
                new FakeDiscoveryPort(), new NoOpObservabilityPort());
    }

    private void declareNotification(String version, Phase phase) {
        registryStore.save(new DesiredState(manifest(NOTIFICATION, version, List.of()), 0L, TargetEnvironment.of("test")));
        registryStore.saveStatus(NOTIFICATION, new ServiceStatus(phase, List.of(), 1L));
    }

    private void declareNewsAtConfigured(String constraint, boolean required) {
        registryStore.save(new DesiredState(manifest(NEWS, "2.4.0",
                List.of(new Dependency(NOTIFICATION, constraint, required))), 0L, TargetEnvironment.of("test")));
        registryStore.saveStatus(NEWS, new ServiceStatus(Phase.CONFIGURED, List.of(), 1L));
    }

    private Condition dependenciesCondition() {
        return registryStore.findStatus(NEWS).orElseThrow().conditions().stream()
                .filter(c -> c.type().equals("DependenciesSatisfied")).findFirst().orElseThrow();
    }

    @Test
    void aRunningRequiredDependencyInAnIncompatibleVersionHoldsTheServiceWithAnExplicitCondition() {
        declareNotification("1.1.0", Phase.RUNNING);
        declareNewsAtConfigured(">=1.2.0", true);

        engine.reconcile(NEWS);

        assertEquals(Phase.CONFIGURED, registryStore.findStatus(NEWS).orElseThrow().phase());
        assertEquals(0, deploymentPort.createCallCount(NEWS), "aucun deploiement tant que la contrainte n'est pas satisfaite");
        Condition condition = dependenciesCondition();
        assertEquals(ConditionStatus.FALSE, condition.status());
        assertEquals("VersionConstraintUnmet", condition.reason());
        assertTrue(condition.message().contains("notification-service"));
        assertTrue(condition.message().contains("1.1.0"));
        assertTrue(condition.message().contains(">=1.2.0"));
    }

    @Test
    void theServiceProceedsOnceTheDependencyIsRedeclaredInACompatibleVersion() {
        declareNotification("1.1.0", Phase.RUNNING);
        declareNewsAtConfigured(">=1.2.0", true);
        engine.reconcile(NEWS);
        assertEquals(Phase.CONFIGURED, registryStore.findStatus(NEWS).orElseThrow().phase());

        declareNotification("1.2.0", Phase.RUNNING); // nouvelle declaration de la dependance

        engine.reconcile(NEWS); // level-triggered : le cycle suivant relit tout

        assertEquals(Phase.DEPLOYING, registryStore.findStatus(NEWS).orElseThrow().phase());
        assertEquals(1, deploymentPort.createCallCount(NEWS));
        Condition condition = dependenciesCondition();
        assertEquals(ConditionStatus.TRUE, condition.status());
        assertEquals("AllSatisfied", condition.reason());
    }

    @Test
    void aCompatibleDependencyThatIsNotRunningYetOnlyMakesTheServiceWait() {
        declareNotification("1.2.0", Phase.DEPLOYING);
        declareNewsAtConfigured(">=1.2.0", true);

        engine.reconcile(NEWS);

        assertEquals(Phase.CONFIGURED, registryStore.findStatus(NEWS).orElseThrow().phase());
        Condition condition = dependenciesCondition();
        assertEquals(ConditionStatus.FALSE, condition.status());
        assertEquals("RequiredDependencyNotRunning", condition.reason());
        assertFalse(condition.message().contains("version"), "pas une incompatibilite de version : " + condition.message());
    }

    @Test
    void anIncompatibleVersionIsReportedEvenIfTheDependencyIsNotRunning() {
        declareNotification("1.1.0", Phase.DEPLOYING); // attendre qu'elle demarre ne changerait rien
        declareNewsAtConfigured(">=1.2.0", true);

        engine.reconcile(NEWS);

        assertEquals("VersionConstraintUnmet", dependenciesCondition().reason());
    }

    @Test
    void anOptionalDependencyInAnIncompatibleVersionNeverBlocksButIsReported() {
        declareNotification("1.1.0", Phase.RUNNING);
        declareNewsAtConfigured(">=1.2.0", false);

        engine.reconcile(NEWS);

        assertEquals(Phase.DEPLOYING, registryStore.findStatus(NEWS).orElseThrow().phase());
        Condition condition = dependenciesCondition();
        assertEquals(ConditionStatus.FALSE, condition.status());
        assertEquals("OptionalDependencyUnavailable", condition.reason()); // doc 10
        assertTrue(condition.message().contains("1.1.0"));
    }

    @Test
    void aRangeConstraintIsEnforcedOnBothBounds() {
        declareNotification("2.0.0", Phase.RUNNING);
        declareNewsAtConfigured(">=1.2.0,<2.0.0", true);

        engine.reconcile(NEWS);

        assertEquals("VersionConstraintUnmet", dependenciesCondition().reason());
        assertEquals(Phase.CONFIGURED, registryStore.findStatus(NEWS).orElseThrow().phase());
    }

    private static ServiceManifest manifest(ServiceId id, String version, List<Dependency> dependencies) {
        return new ServiceManifest(
                id,
                ServiceVersion.parse(version),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry.civitas.africa/" + id.value() + ":" + version, "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/" + id.value() + ":" + version,
                        "500m", "512Mi", new ReplicaRange(2, 6)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(Duration.ofMillis(50)), dependencies);
    }
}
