package africa.civitas.egen.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceManifestTest {

    private ServiceRuntime aRuntime() {
        return new ServiceRuntime(RuntimeType.CONTAINER,
                "registry.civitas.africa/news-service:2.4.0", "python");
    }

    private DeploymentSpec aDeploymentSpec() {
        return new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0",
                "500m", "512Mi", new ReplicaRange(2, 6));
    }

    private HealthSpec aHealthSpec() {
        return new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3);
    }

    @Test
    void buildsAValidManifest() {
        ServiceManifest manifest = new ServiceManifest(
                ServiceId.of("news-service"), ServiceVersion.parse("2.4.0"), aRuntime(),
                aDeploymentSpec(), aHealthSpec(), LifecyclePolicy.defaultPolicy());
        assertEquals("news-service", manifest.id().value());
        assertEquals("2.4.0", manifest.version().toString());
    }

    @Test
    void defaultsTheLifecyclePolicyWhenOmitted() {
        ServiceManifest manifest = new ServiceManifest(
                ServiceId.of("news-service"), ServiceVersion.parse("2.4.0"), aRuntime(),
                aDeploymentSpec(), aHealthSpec(), null);
        assertEquals(Duration.ofSeconds(30), manifest.lifecycle().shutdownGracePeriod());
    }

    @Test
    void rejectsAMissingId() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceManifest(null, ServiceVersion.parse("2.4.0"), aRuntime(),
                        aDeploymentSpec(), aHealthSpec(), LifecyclePolicy.defaultPolicy()));
    }

    @Test
    void rejectsAMissingDeploymentSpec() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceManifest(ServiceId.of("news-service"),
                        ServiceVersion.parse("2.4.0"), aRuntime(), null, aHealthSpec(),
                        LifecyclePolicy.defaultPolicy()));
    }

    @Test
    void rejectsAMissingHealthSpec() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceManifest(ServiceId.of("news-service"),
                        ServiceVersion.parse("2.4.0"), aRuntime(), aDeploymentSpec(), null,
                        LifecyclePolicy.defaultPolicy()));
    }
}
