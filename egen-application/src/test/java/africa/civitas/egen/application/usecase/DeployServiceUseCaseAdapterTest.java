package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.DeploymentAdapterCatalog;
import africa.civitas.egen.application.reconciliation.PendingDeclareTraces;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation à Declare : adapter activé (06, étape 3) et immuable (07, « Sélection et routage »). */
class DeployServiceUseCaseAdapterTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final TargetEnvironment ENV = TargetEnvironment.of("test");

    private final InMemoryRegistryStore registry = new InMemoryRegistryStore();
    private final WorkQueue queue = new WorkQueue();
    private final DeployServiceUseCase useCase = new DeployServiceUseCaseImpl(registry, queue,
            new PendingDeclareTraces(), DeploymentAdapterCatalog.of(Set.of("nomad", "kubernetes")));

    private static ServiceManifest manifest(String adapter) {
        return new ServiceManifest(NEWS, ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry/news:1.0.0", "java"),
                new DeploymentSpec(adapter, "registry/news:1.0.0", "500m", "512Mi", new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                LifecyclePolicy.defaultPolicy());
    }

    @Test
    void anEnabledAdapterIsAccepted() {
        assertEquals(1L, useCase.declare(manifest("kubernetes"), ENV).generation());
        assertEquals(1L, new DeployServiceUseCaseImpl(new InMemoryRegistryStore(), new WorkQueue(),
                new PendingDeclareTraces(), DeploymentAdapterCatalog.of(Set.of("nomad")))
                .declare(manifest("nomad"), ENV).generation());
    }

    @Test
    void anAdapterThatIsNotEnabledIsRefusedBeforeAnythingIsPersistedOrQueued() {
        UnknownDeploymentAdapterException e = assertThrows(UnknownDeploymentAdapterException.class,
                () -> useCase.declare(manifest("docker"), ENV));

        assertTrue(e.getMessage().contains("\"docker\""), e.getMessage());
        assertTrue(e.getMessage().contains("kubernetes, nomad"), e.getMessage());
        assertTrue(registry.findById(NEWS).isEmpty(), "un refus ne laisse aucune trace");
        queue.shutdown();
        assertThrows(InterruptedException.class, queue::take, "rien n'a été mis en file");
        assertTrue(e instanceof IllegalArgumentException, "l'API répond 400 via IllegalArgumentException");
    }

    @Test
    void redeclaringTheSameServiceOnTheSameAdapterCreatesANewGeneration() {
        useCase.declare(manifest("nomad"), ENV);
        assertEquals(2L, useCase.declare(manifest("nomad"), ENV).generation());
    }

    @Test
    void changingTheAdapterOfAnExistingServiceIsRefusedAndTheRegistryIsUntouched() {
        useCase.declare(manifest("nomad"), ENV);

        DeploymentAdapterChangeException e = assertThrows(DeploymentAdapterChangeException.class,
                () -> useCase.declare(manifest("kubernetes"), ENV));

        assertTrue(e.getMessage().contains("\"nomad\"") && e.getMessage().contains("\"kubernetes\""), e.getMessage());
        assertEquals(1L, registry.findById(NEWS).orElseThrow().generation(), "la génération 1 reste la seule");
        assertEquals("nomad", registry.findById(NEWS).orElseThrow().manifest().deployment().adapter());
    }

    @Test
    void theCatalogRejectsAnEmptyListAndSortsItsNames() {
        assertThrows(IllegalArgumentException.class, () -> DeploymentAdapterCatalog.of(Set.of()));
        assertEquals(java.util.List.of("kubernetes", "nomad"),
                java.util.List.copyOf(DeploymentAdapterCatalog.of(Set.of("nomad", "kubernetes")).enabledNames()));
        assertFalse(DeploymentAdapterCatalog.of(Set.of("nomad")).isEnabled("kubernetes"));
    }
}
