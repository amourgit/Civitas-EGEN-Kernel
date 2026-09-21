package africa.civitas.egen.application.usecase;

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
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetServiceStatusUseCaseImplTest {

    private static final ServiceId ID = ServiceId.of("news-service");

    @Test
    void throwsWhenTheServiceWasNeverDeclared() {
        GetServiceStatusUseCase useCase = new GetServiceStatusUseCaseImpl(new InMemoryRegistryStore());
        assertThrows(ServiceNotFoundException.class, () -> useCase.getStatus(ID));
    }

    @Test
    void returnsTheInitialStatusRightAfterDeclarationBeforeAnyReconciliation() {
        InMemoryRegistryStore registry = new InMemoryRegistryStore();
        registry.save(new DesiredState(aManifest(), 0L, TargetEnvironment.of("test")));

        GetServiceStatusUseCase useCase = new GetServiceStatusUseCaseImpl(registry);

        ServiceStatus status = useCase.getStatus(ID);
        assertEquals(Phase.DECLARED, status.phase());
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
