package africa.civitas.egen.domain.model;

import africa.civitas.egen.domain.model.ResiliencyPolicy.Backoff;
import africa.civitas.egen.domain.model.ResiliencyPolicy.CircuitBreakerSpec;
import africa.civitas.egen.domain.model.ResiliencyPolicy.RetrySpec;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResiliencyPolicyTest {

    @Test
    void defaultsMatchTheValuesDocumentedInDoc06() {
        ResiliencyPolicy defaults = ResiliencyPolicy.defaults();

        assertEquals(Duration.ofSeconds(30), defaults.timeout());
        assertEquals(1, defaults.retry().maxAttempts());
        assertEquals(Backoff.FIXED, defaults.retry().backoff());
        assertEquals(Duration.ofMillis(200), defaults.retry().baseDelay());
        assertEquals(5, defaults.circuitBreaker().failureThreshold());
        assertEquals(Duration.ofSeconds(30), defaults.circuitBreaker().openDuration());
    }

    @Test
    void omittedPartsTakeTheirDefaults() {
        ResiliencyPolicy policy = new ResiliencyPolicy(Duration.ofSeconds(10), null, null);

        assertEquals(Duration.ofSeconds(10), policy.timeout());
        assertEquals(RetrySpec.defaults(), policy.retry());
        assertEquals(CircuitBreakerSpec.defaults(), policy.circuitBreaker());
    }

    @Test
    void nullBackoffAndDelaysInsideASpecTakeTheirDefaults() {
        RetrySpec retry = new RetrySpec(3, null, null);

        assertEquals(Backoff.FIXED, retry.backoff());
        assertEquals(RetrySpec.DEFAULT_BASE_DELAY, retry.baseDelay());
        assertEquals(CircuitBreakerSpec.DEFAULT_OPEN_DURATION, new CircuitBreakerSpec(2, null).openDuration());
    }

    @Test
    void maxAttemptsMustBeBetween1AndTheLimit() {
        new RetrySpec(1, Backoff.FIXED, Duration.ofMillis(1));
        new RetrySpec(ResiliencyPolicy.MAX_ATTEMPTS_LIMIT, Backoff.FIXED, Duration.ofMillis(1));
        for (int invalid : new int[] {0, -1, ResiliencyPolicy.MAX_ATTEMPTS_LIMIT + 1}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new RetrySpec(invalid, Backoff.FIXED, Duration.ofMillis(1)));
            assertTrue(e.getMessage().contains("maxAttempts"), e.getMessage());
        }
    }

    @Test
    void durationsMustBeStrictlyPositive() {
        for (Duration invalid : new Duration[] {Duration.ZERO, Duration.ofSeconds(-1)}) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> new ResiliencyPolicy(invalid, null, null)).getMessage().contains("timeout"));
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> new RetrySpec(2, Backoff.FIXED, invalid)).getMessage().contains("baseDelay"));
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> new CircuitBreakerSpec(3, invalid)).getMessage().contains("openDuration"));
        }
    }

    @Test
    void failureThresholdMustBeAtLeastOne() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new CircuitBreakerSpec(0, Duration.ofSeconds(1)))
                .getMessage().contains("failureThreshold"));
        new CircuitBreakerSpec(1, Duration.ofSeconds(1));
    }
}
