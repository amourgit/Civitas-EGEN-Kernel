package africa.civitas.egen.api.mapper;

import africa.civitas.egen.api.dto.ResiliencyDto;
import africa.civitas.egen.api.dto.ResiliencyViewDto;
import africa.civitas.egen.api.dto.ServiceManifestDto;
import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ResiliencyPolicy.Backoff;
import africa.civitas.egen.domain.model.ServiceManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResiliencyMapperTest {

    private static final ObjectMapper YAML = new YAMLMapper();

    private static final String MANIFEST_YAML = """
            apiVersion: egen.civitas.africa/v1
            kind: ServiceManifest
            metadata:
              name: notification-service
              version: 1.2.0
              team: content-platform
            runtime:
              type: container
              artifact: registry.civitas.africa/notification-service:1.2.0
              language: java
            deployment:
              adapter: nomad
              image: registry.civitas.africa/notification-service:1.2.0
              resources: { cpu: 500m, memory: 512Mi }
              replicas: { min: 2, max: 6 }
            health:
              http: { endpoint: /health, interval: 10s, timeout: 2s, failuresBeforeUnhealthy: 3 }
            %s
            """;

    private static ServiceManifest manifest(String resiliencyBlock) throws Exception {
        return ServiceManifestMapper.toDomain(
                YAML.readValue(MANIFEST_YAML.formatted(resiliencyBlock), ServiceManifestDto.class));
    }

    @Test
    void theResiliencyBlockOfDoc06MapsToTheDomain() throws Exception {
        ServiceManifest manifest = manifest("""
                resiliency:
                  timeout: 10s
                  retry:
                    maxAttempts: 3
                    backoff: exponential
                    baseDelay: 200ms
                  circuitBreaker:
                    failureThreshold: 5
                    openDuration: 30s""");

        ResiliencyPolicy policy = manifest.resiliency();
        assertEquals(Duration.ofSeconds(10), policy.timeout());
        assertEquals(3, policy.retry().maxAttempts());
        assertEquals(Backoff.EXPONENTIAL, policy.retry().backoff());
        assertEquals(Duration.ofMillis(200), policy.retry().baseDelay());
        assertEquals(5, policy.circuitBreaker().failureThreshold());
        assertEquals(Duration.ofSeconds(30), policy.circuitBreaker().openDuration());
    }

    @Test
    void aManifestWithoutTheBlockStaysValidAndReceivesTheDefaults() throws Exception {
        assertEquals(ResiliencyPolicy.defaults(), manifest("").resiliency());
    }

    @Test
    void partiallyDeclaredBlocksKeepTheDefaultsForTheOmittedFields() throws Exception {
        ResiliencyPolicy policy = manifest("""
                resiliency:
                  retry: { maxAttempts: 4 }
                  circuitBreaker: { failureThreshold: 2 }""").resiliency();

        assertEquals(ResiliencyPolicy.DEFAULT_TIMEOUT, policy.timeout());
        assertEquals(4, policy.retry().maxAttempts());
        assertEquals(Backoff.FIXED, policy.retry().backoff());
        assertEquals(ResiliencyPolicy.RetrySpec.DEFAULT_BASE_DELAY, policy.retry().baseDelay());
        assertEquals(2, policy.circuitBreaker().failureThreshold());
        assertEquals(ResiliencyPolicy.CircuitBreakerSpec.DEFAULT_OPEN_DURATION, policy.circuitBreaker().openDuration());
    }

    @Test
    void durationsAcceptMillisecondsSecondsMinutesAndHours() {
        assertEquals(Duration.ofMillis(200), ResiliencyMapper.parseDuration("200ms", "t"));
        assertEquals(Duration.ofSeconds(10), ResiliencyMapper.parseDuration("10s", "t"));
        assertEquals(Duration.ofMinutes(5), ResiliencyMapper.parseDuration("5m", "t"));
        assertEquals(Duration.ofHours(1), ResiliencyMapper.parseDuration("1h", "t"));
        for (String bad : new String[] {"10", "ms", "1.5s", "-3s", "5d", "", "PT5S", "10 s"}) {
            assertThrows(IllegalArgumentException.class, () -> ResiliencyMapper.parseDuration(bad, "t"), bad);
        }
    }

    @Test
    void invalidContentIsRejectedWithAMessageNamingTheFaultyField() {
        ResiliencyDto badTimeout = new ResiliencyDto();
        badTimeout.timeout = "dix secondes";
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ResiliencyMapper.toDomain(badTimeout))
                .getMessage().contains("resiliency.timeout"));

        ResiliencyDto badBackoff = new ResiliencyDto();
        badBackoff.retry = new ResiliencyDto.RetryDto();
        badBackoff.retry.backoff = "quadratic";
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ResiliencyMapper.toDomain(badBackoff))
                .getMessage().contains("retry.backoff"));

        ResiliencyDto outOfBounds = new ResiliencyDto();
        outOfBounds.retry = new ResiliencyDto.RetryDto();
        outOfBounds.retry.maxAttempts = 11;
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ResiliencyMapper.toDomain(outOfBounds))
                .getMessage().contains("maxAttempts"));

        ResiliencyDto zeroThreshold = new ResiliencyDto();
        zeroThreshold.circuitBreaker = new ResiliencyDto.CircuitBreakerDto();
        zeroThreshold.circuitBreaker.failureThreshold = 0;
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ResiliencyMapper.toDomain(zeroThreshold))
                .getMessage().contains("failureThreshold"));
    }

    @Test
    void anAbsentBlockMapsToNullSoTheManifestAppliesItsDefaults() {
        assertNull(ResiliencyMapper.toDomain(null));
    }

    @Test
    void theViewExposesEffectiveValuesInMillisecondsWithLowercaseBackoff() {
        ResiliencyViewDto view = ResiliencyMapper.toView(new ResiliencyPolicy(Duration.ofSeconds(10),
                new ResiliencyPolicy.RetrySpec(3, Backoff.EXPONENTIAL, Duration.ofMillis(200)),
                new ResiliencyPolicy.CircuitBreakerSpec(5, Duration.ofSeconds(30))));

        assertEquals(10_000, view.timeoutMillis);
        assertEquals(3, view.retry.maxAttempts);
        assertEquals("exponential", view.retry.backoff);
        assertEquals(200, view.retry.baseDelayMillis);
        assertEquals(5, view.circuitBreaker.failureThreshold);
        assertEquals(30_000, view.circuitBreaker.openDurationMillis);
    }

    @Test
    void theViewOfTheDefaultsMatchesTheSharedContractDefaults() {
        ResiliencyViewDto view = ResiliencyMapper.toView(ResiliencyPolicy.defaults());

        assertEquals(30_000, view.timeoutMillis);
        assertEquals(1, view.retry.maxAttempts);
        assertEquals("fixed", view.retry.backoff);
        assertEquals(200, view.retry.baseDelayMillis);
        assertEquals(5, view.circuitBreaker.failureThreshold);
        assertEquals(30_000, view.circuitBreaker.openDurationMillis);
    }
}
