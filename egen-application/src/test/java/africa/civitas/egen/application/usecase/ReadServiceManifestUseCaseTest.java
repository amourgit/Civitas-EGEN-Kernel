package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadServiceManifestUseCaseTest {

    private static DesiredState desired(String name) {
        return new DesiredState(new ServiceManifest(ServiceId.of(name), ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry/x:1", "java"),
                new DeploymentSpec("nomad", "registry/x:1", "500m", "512Mi", new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3), null),
                1L, TargetEnvironment.of("test"));
    }

    @Test
    void getReturnsTheDeclaredDesiredState() {
        InMemoryRegistryStore registry = new InMemoryRegistryStore();
        registry.save(desired("news-service"));

        DesiredState state = new ReadServiceManifestUseCaseImpl(registry).get(ServiceId.of("news-service"));

        assertEquals("news-service", state.manifest().id().value());
    }

    @Test
    void getOfAnUndeclaredServiceIsReportedExplicitly() {
        assertThrows(ServiceNotFoundException.class,
                () -> new ReadServiceManifestUseCaseImpl(new InMemoryRegistryStore()).get(ServiceId.of("ghost")));
    }

    @Test
    void listReturnsEveryDeclaredServiceSortedById() {
        InMemoryRegistryStore registry = new InMemoryRegistryStore();
        registry.save(desired("storage-service"));
        registry.save(desired("auth-service"));
        registry.save(desired("news-service"));

        List<String> ids = new ReadServiceManifestUseCaseImpl(registry).list().stream()
                .map(d -> d.manifest().id().value()).toList();

        assertEquals(List.of("auth-service", "news-service", "storage-service"), ids);
    }

    @Test
    void listOfAnEmptyRegistryIsEmpty() {
        assertTrue(new ReadServiceManifestUseCaseImpl(new InMemoryRegistryStore()).list().isEmpty());
    }
}
