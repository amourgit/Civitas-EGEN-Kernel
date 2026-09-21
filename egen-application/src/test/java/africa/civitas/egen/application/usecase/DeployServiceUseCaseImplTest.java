package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.application.reconciliation.WorkQueue;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployServiceUseCaseImplTest {

    @Test
    void persistsTheDesiredStateAndEnqueuesReconciliationWithoutCallingAnyAdapter()
            throws InterruptedException {
        InMemoryRegistryStore registry = new InMemoryRegistryStore();
        WorkQueue workQueue = new WorkQueue();
        DeployServiceUseCase useCase = new DeployServiceUseCaseImpl(registry, workQueue);

        ServiceManifest manifest = new ServiceManifest(
                ServiceId.of("news-service"), ServiceVersion.parse("2.4.0"),
                new ServiceRuntime(RuntimeType.CONTAINER,
                        "registry.civitas.africa/news-service:2.4.0", "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0",
                        "500m", "512Mi", new ReplicaRange(2, 6)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                LifecyclePolicy.defaultPolicy());

        DeclareResult result = useCase.declare(manifest, TargetEnvironment.of("production"));

        assertEquals(ServiceId.of("news-service"), result.serviceId());
        assertEquals(1L, result.generation());
        assertTrue(registry.findById(result.serviceId()).isPresent());

        // La cle est bien enqueuee pour reconciliation asynchrone (pas d'appel
        // synchrone au deploiement ici — voir 04.5).
        ServiceId queued = workQueue.take();
        assertEquals(result.serviceId(), queued);
    }
}
