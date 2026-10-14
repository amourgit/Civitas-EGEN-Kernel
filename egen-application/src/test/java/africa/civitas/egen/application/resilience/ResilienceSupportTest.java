package africa.civitas.egen.application.resilience;

import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.OwnerTeam;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ResiliencyPolicy.CircuitBreakerSpec;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResilienceSupportTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");

    private static final class FakeClock extends Clock {
        Instant now = Instant.parse("2026-10-09T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static ServiceManifest manifest(ServiceId id, ResiliencyPolicy policy) {
        return new ServiceManifest(id, ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry/x:1", "java"),
                new DeploymentSpec("nomad", "registry/x:1", "500m", "512Mi", new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3), null,
                List.of(), OwnerTeam.unassigned(), policy);
    }

    // ------------------------------------------------------------------ classification

    @Test
    void classificationMatchesTheNormativeTable() {
        for (int retryable : new int[] {500, 502, 503, 599, 408, 429}) {
            assertTrue(CallClassification.isRetryableFailure(retryable), "" + retryable);
        }
        for (int notRetryable : new int[] {200, 204, 301, 400, 401, 403, 404, 409, 422}) {
            assertFalse(CallClassification.isRetryableFailure(notRetryable), "" + notRetryable);
        }
    }

    // ------------------------------------------------------------------ registre de circuits

    @Test
    void oneCircuitPerTargetServiceSharedByAllCallers() {
        ServiceCircuitBreakers breakers = new ServiceCircuitBreakers(new FakeClock());
        CircuitBreakerSpec spec = CircuitBreakerSpec.defaults();

        assertSame(breakers.forService(NEWS, spec), breakers.forService(NEWS, spec));
        assertNotSame(breakers.forService(NEWS, spec), breakers.forService(ServiceId.of("other-service"), spec));
    }

    @Test
    void aRedeclaredThresholdRecreatesTheCircuitClosed() {
        ServiceCircuitBreakers breakers = new ServiceCircuitBreakers(new FakeClock());
        CircuitBreaker first = breakers.forService(NEWS, new CircuitBreakerSpec(1, Duration.ofSeconds(30)));
        first.tryAcquire();
        first.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, first.state());

        CircuitBreaker second = breakers.forService(NEWS, new CircuitBreakerSpec(3, Duration.ofSeconds(30)));

        assertNotSame(first, second);
        assertEquals(CircuitBreaker.State.CLOSED, second.state());
    }

    // ------------------------------------------------------------------ circuit : cas hors contrat

    @Test
    void aResultRecordedWhileOpenNeverChangesTheState() {
        // Appel contourne (ex. une compensation) pendant que le circuit est ouvert.
        FakeClock clock = new FakeClock();
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerSpec(1, Duration.ofSeconds(10)), clock);
        breaker.tryAcquire();
        breaker.recordFailure();

        breaker.recordSuccess();
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        breaker.recordFailure();
        clock.now = clock.now.plusSeconds(10);
        assertTrue(breaker.tryAcquire(), "la fenetre d'ouverture n'a pas ete prolongee par l'echec contourne");
    }

    @Test
    void onlyOneProbeIsAdmittedWhileHalfOpen() {
        FakeClock clock = new FakeClock();
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerSpec(1, Duration.ofSeconds(10)), clock);
        breaker.tryAcquire();
        breaker.recordFailure();
        clock.now = clock.now.plusSeconds(10);

        assertTrue(breaker.tryAcquire());
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());
        assertFalse(breaker.tryAcquire(), "un seul essai a la fois en semi-ouvert");
    }

    // ------------------------------------------------------------------ providers

    @Test
    void theRegistryProviderReadsTheDeclaredPolicyAndFallsBackToDefaults() {
        InMemoryRegistryStore registry = new InMemoryRegistryStore();
        ResiliencyPolicy declared = new ResiliencyPolicy(Duration.ofSeconds(5), null,
                new CircuitBreakerSpec(2, Duration.ofSeconds(7)));
        registry.save(new DesiredState(manifest(NEWS, declared), 0L, TargetEnvironment.of("test")));
        RegistryResiliencyPolicyProvider provider = new RegistryResiliencyPolicyProvider(registry);

        assertEquals(declared, provider.policyFor(NEWS));
        assertEquals(ResiliencyPolicy.defaults(), provider.policyFor(ServiceId.of("unknown-service")));
    }

    @Test
    void theCachingProviderHitsTheDelegateOncePerTtlAndPerService() {
        FakeClock clock = new FakeClock();
        AtomicInteger lookups = new AtomicInteger();
        CachingResiliencyPolicyProvider provider = new CachingResiliencyPolicyProvider(
                target -> {
                    lookups.incrementAndGet();
                    return ResiliencyPolicy.defaults();
                }, clock, Duration.ofSeconds(5));

        provider.policyFor(NEWS);
        provider.policyFor(NEWS);
        assertEquals(1, lookups.get());

        provider.policyFor(ServiceId.of("other-service"));
        assertEquals(2, lookups.get());

        clock.now = clock.now.plusSeconds(5);
        provider.policyFor(NEWS);
        assertEquals(3, lookups.get(), "au-dela du TTL, la politique est relue (manifeste redeclare)");
    }
}
