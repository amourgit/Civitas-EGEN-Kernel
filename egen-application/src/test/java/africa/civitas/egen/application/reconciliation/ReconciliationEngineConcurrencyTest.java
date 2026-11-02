package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentHandle;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DeploymentObservation;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 2 (docs 17), avec de vrais threads. Contrat : 04, « Composants de la boucle » — concurrence
 * bornee ({@code maxConcurrentReconciles}) et un service jamais reconcilie par deux workers a la fois.
 */
class ReconciliationEngineConcurrencyTest {

    /** Delegue au double habituel ; {@code create} dure assez longtemps pour que les workers se chevauchent. */
    private static final class TrackingDeploymentPort implements DeploymentPort {
        private final FakeDeploymentPort delegate = new FakeDeploymentPort();
        private final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        private final Map<ServiceId, AtomicInteger> inFlightByService = new ConcurrentHashMap<>();
        final AtomicInteger maxInFlightForOneService = new AtomicInteger();

        @Override
        public DeploymentHandle create(ServiceId id, DeploymentSpec spec) {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            int sameService = inFlightByService.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            maxInFlightForOneService.accumulateAndGet(sameService, Math::max);
            try {
                Thread.sleep(80);
                return delegate.create(id, spec);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                inFlightByService.get(id).decrementAndGet();
                inFlight.decrementAndGet();
            }
        }

        @Override public void update(ServiceId id, DeploymentSpec newSpec) { delegate.update(id, newSpec); }
        @Override public void scale(ServiceId id, int replicas) { delegate.scale(id, replicas); }
        @Override public void restart(ServiceId id) { delegate.restart(id); }
        @Override public void stop(ServiceId id) { delegate.stop(id); }
        @Override public void remove(ServiceId id) { delegate.remove(id); }
        @Override public DeploymentObservation getStatus(ServiceId id) { return delegate.getStatus(id); }
        @Override public List<AllocationInfo> listAllocations(ServiceId id) { return delegate.listAllocations(id); }
        @Override public void rollback(ServiceId id, ServiceVersion targetVersion) { delegate.rollback(id, targetVersion); }
    }

    private static ServiceManifest manifest(ServiceId id) {
        return new ServiceManifest(id, ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry.civitas.africa/" + id.value() + ":1.0.0", "java"),
                new DeploymentSpec("nomad", "registry.civitas.africa/" + id.value() + ":1.0.0",
                        "500m", "512Mi", new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(Duration.ofMillis(50)));
    }

    private static List<ServiceId> declareConfigured(InMemoryRegistryStore registry, int count) {
        List<ServiceId> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ServiceId id = ServiceId.of("svc-" + i);
            registry.save(new DesiredState(manifest(id), 0L, TargetEnvironment.of("test")));
            registry.saveStatus(id, new ServiceStatus(Phase.CONFIGURED, List.of(), 1L));
            ids.add(id);
        }
        return ids;
    }

    private static void awaitAllDeploying(InMemoryRegistryStore registry, List<ServiceId> ids) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (ids.stream().allMatch(id -> registry.findStatus(id).map(s -> s.phase() != Phase.CONFIGURED).orElse(false))) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("tous les services devaient quitter CONFIGURED : " + ids.stream()
                .map(id -> id.value() + "=" + registry.findStatus(id).map(ServiceStatus::phase).orElse(null)).toList());
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void severalServicesAreReconciledAtTheSameTimeUpToTheConfiguredBound() throws Exception {
        InMemoryRegistryStore registry = new InMemoryRegistryStore();
        TrackingDeploymentPort deployment = new TrackingDeploymentPort();
        WorkQueue queue = new WorkQueue();
        List<ServiceId> ids = declareConfigured(registry, 8);
        try (ReconciliationEngine engine = new ReconciliationEngine(queue, registry, deployment,
                new FakeDiscoveryPort(), new NoOpObservabilityPort(), new PendingDeclareTraces(),
                ServiceBindings.unavailable(), 3)) {
            engine.start();
            queue.enqueueAll(ids);

            awaitAllDeploying(registry, ids);
        }

        assertTrue(deployment.maxInFlight.get() >= 2, "plusieurs workers doivent se chevaucher : " + deployment.maxInFlight);
        assertTrue(deployment.maxInFlight.get() <= 3, "la concurrence est bornee a 3 : " + deployment.maxInFlight);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void aSingleWorkerNeverOverlaps() throws Exception {
        InMemoryRegistryStore registry = new InMemoryRegistryStore();
        TrackingDeploymentPort deployment = new TrackingDeploymentPort();
        WorkQueue queue = new WorkQueue();
        List<ServiceId> ids = declareConfigured(registry, 4);
        try (ReconciliationEngine engine = new ReconciliationEngine(queue, registry, deployment,
                new FakeDiscoveryPort(), new NoOpObservabilityPort(), new PendingDeclareTraces(),
                ServiceBindings.unavailable(), 1)) {
            engine.start();
            queue.enqueueAll(ids);

            awaitAllDeploying(registry, ids);
        }

        assertEquals(1, deployment.maxInFlight.get());
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void aServiceIsNeverReconciledByTwoWorkersEvenWhenItKeepsBeingEnqueued() throws Exception {
        InMemoryRegistryStore registry = new InMemoryRegistryStore();
        TrackingDeploymentPort deployment = new TrackingDeploymentPort();
        WorkQueue queue = new WorkQueue();
        List<ServiceId> ids = declareConfigured(registry, 2);
        try (ReconciliationEngine engine = new ReconciliationEngine(queue, registry, deployment,
                new FakeDiscoveryPort(), new NoOpObservabilityPort(), new PendingDeclareTraces(),
                ServiceBindings.unavailable(), 4)) {
            engine.start();
            for (int i = 0; i < 25; i++) { // declarations / resyncs a repetition pendant les reconciliations
                queue.enqueueAll(ids);
                Thread.sleep(10);
            }

            awaitAllDeploying(registry, ids);
        }

        assertEquals(1, deployment.maxInFlightForOneService.get(), "deux workers ont traite le meme service en parallele");
    }

    @Test
    void theBoundMustBeAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> new ReconciliationEngine(new WorkQueue(),
                new InMemoryRegistryStore(), new FakeDeploymentPort(), new FakeDiscoveryPort(),
                new NoOpObservabilityPort(), new PendingDeclareTraces(), ServiceBindings.unavailable(), 0));
    }
}
