package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.application.reconciliation.WorkQueue;
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
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StopServiceUseCaseImplTest {

    private static final ServiceId ID = ServiceId.of("news-service");

    @Test
    void throwsWhenTheServiceWasNeverDeclared() {
        StopServiceUseCase useCase = new StopServiceUseCaseImpl(new InMemoryRegistryStore(), new WorkQueue());
        assertThrows(ServiceNotFoundException.class, () -> useCase.stop(ID));
    }

    @Test
    void movesARunningServiceToStoppingAndEnqueuesReconciliation() throws InterruptedException {
        InMemoryRegistryStore registry = new InMemoryRegistryStore();
        registry.save(new DesiredState(aManifest(), 0L, TargetEnvironment.of("test")));
        registry.saveStatus(ID, new ServiceStatus(Phase.RUNNING, java.util.List.of(), 1L));
        WorkQueue workQueue = new WorkQueue();
        StopServiceUseCase useCase = new StopServiceUseCaseImpl(registry, workQueue);

        useCase.stop(ID);

        assertEquals(Phase.STOPPING, registry.findStatus(ID).orElseThrow().phase());
        assertEquals(ID, workQueue.take());
    }

    @Test
    void isIdempotentWhenAlreadyStoppingOrStopped() {
        InMemoryRegistryStore registry = new InMemoryRegistryStore();
        registry.save(new DesiredState(aManifest(), 0L, TargetEnvironment.of("test")));
        registry.saveStatus(ID, new ServiceStatus(Phase.STOPPED, java.util.List.of(), 1L));
        StopServiceUseCase useCase = new StopServiceUseCaseImpl(registry, new WorkQueue());

        useCase.stop(ID); // ne doit pas lever d'exception ni changer la phase

        assertEquals(Phase.STOPPED, registry.findStatus(ID).orElseThrow().phase());
    }

    private static ServiceManifest aManifest() {
        return new ServiceManifest(ID, ServiceVersion.parse("2.4.0"),
                new ServiceRuntime(RuntimeType.CONTAINER,
                        "registry.civitas.africa/news-service:2.4.0", "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0",
                        "500m", "512Mi", new ReplicaRange(2, 6)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                LifecyclePolicy.defaultPolicy());
    }
}
