package africa.civitas.egen.api.dto;

/**
 * Politique de resilience EFFECTIVE (defauts appliques), en millisecondes,
 * telle que servie par {@code GET /api/v1/discover/{serviceId}} aux SDK et au
 * sidecar (voir docs/architecture/13-api-et-contrats.md).
 */
public class ResiliencyViewDto {
    public long timeoutMillis;
    public Retry retry;
    public CircuitBreaker circuitBreaker;

    public static class Retry {
        public int maxAttempts;
        public String backoff;
        public long baseDelayMillis;
    }

    public static class CircuitBreaker {
        public int failureThreshold;
        public long openDurationMillis;
    }
}
