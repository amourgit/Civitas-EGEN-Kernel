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

    @Test
    void aManifestWithoutAResiliencyBlockReceivesTheDefaults() {
        ServiceManifest manifest = new ServiceManifest(
                ServiceId.of("news-service"), ServiceVersion.parse("2.4.0"), aRuntime(),
                aDeploymentSpec(), aHealthSpec(), null);

        assertEquals(ResiliencyPolicy.defaults(), manifest.resiliency());
    }

    @Test
    void theHistoricalEightArgumentConstructorStillWorksAndKeepsTheDefaults() {
        ServiceManifest manifest = new ServiceManifest(
                ServiceId.of("news-service"), ServiceVersion.parse("2.4.0"), aRuntime(),
                aDeploymentSpec(), aHealthSpec(), null, java.util.List.of(), OwnerTeam.unassigned());

        assertEquals(ResiliencyPolicy.defaults(), manifest.resiliency());
    }

    @Test
    void aDeclaredResiliencyPolicyIsKept() {
        ResiliencyPolicy policy = new ResiliencyPolicy(Duration.ofSeconds(10),
                new ResiliencyPolicy.RetrySpec(3, ResiliencyPolicy.Backoff.EXPONENTIAL, Duration.ofMillis(100)),
                new ResiliencyPolicy.CircuitBreakerSpec(2, Duration.ofSeconds(5)));
        ServiceManifest manifest = new ServiceManifest(
                ServiceId.of("news-service"), ServiceVersion.parse("2.4.0"), aRuntime(),
                aDeploymentSpec(), aHealthSpec(), null, java.util.List.of(), OwnerTeam.unassigned(), policy);

        assertEquals(policy, manifest.resiliency());
    }

    /** Fixture partagee : un manifeste valide portant les {@code interfaces} donnees (docs/architecture/21.3). */
    ServiceManifest manifestWith(java.util.List<ServiceInterface> interfaces) {
        return new ServiceManifest(ServiceId.of("news-service"), ServiceVersion.parse("2.4.0"), aRuntime(),
                aDeploymentSpec(), aHealthSpec(), null, java.util.List.of(), OwnerTeam.unassigned(), null,
                null, null, interfaces);
    }

    @Test
    void aManifestWithoutTheNewBlocksHasNoEventsNoConfigurationNoInterfaces() {
        ServiceManifest manifest = manifestWith(null);

        assertEquals(EventSpec.none(), manifest.events());
        assertEquals(ConfigurationSpec.none(), manifest.configuration());
        assertEquals(java.util.List.of(), manifest.interfaces());
    }

    @Test
    void theHistoricalNineArgumentConstructorStillWorks() {
        ServiceManifest manifest = new ServiceManifest(ServiceId.of("news-service"), ServiceVersion.parse("2.4.0"),
                aRuntime(), aDeploymentSpec(), aHealthSpec(), null, java.util.List.of(), OwnerTeam.unassigned(),
                ResiliencyPolicy.defaults());

        assertEquals(EventSpec.none(), manifest.events());
    }

    @Test
    void eventsAndConfigurationAreKept() {
        EventSpec events = new EventSpec(java.util.List.of(new EventSpec.Published("a.b.v1", null)), null);
        ConfigurationSpec configuration = new ConfigurationSpec(
                java.util.List.of(new ConfigurationSpec.Key("A", "boolean", "true")), null);
        ServiceManifest manifest = new ServiceManifest(ServiceId.of("news-service"), ServiceVersion.parse("2.4.0"),
                aRuntime(), aDeploymentSpec(), aHealthSpec(), null, java.util.List.of(), OwnerTeam.unassigned(), null,
                events, configuration, null);

        assertEquals(events, manifest.events());
        assertEquals(configuration, manifest.configuration());
    }
}
