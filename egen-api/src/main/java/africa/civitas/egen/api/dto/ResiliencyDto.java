package africa.civitas.egen.api.dto;

/**
 * Bloc {@code resiliency} du ServiceManifest (voir
 * docs/architecture/06-service-manifest.md). Durees en texte avec unite
 * ({@code 200ms}, {@code 10s}, {@code 5m}, {@code 1h}).
 */
public class ResiliencyDto {
    public String timeout;
    public RetryDto retry;
    public CircuitBreakerDto circuitBreaker;

    public static class RetryDto {
        public Integer maxAttempts;
        public String backoff;
        public String baseDelay;
    }

    public static class CircuitBreakerDto {
        public Integer failureThreshold;
        public String openDuration;
    }
}
