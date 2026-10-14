package africa.civitas.egen.sdk;

import java.util.Map;

/**
 * Politique de resilience d'un service cible, telle que servie par
 * {@code GET /api/v1/discover/{service}} (docs/architecture/13) — valeurs
 * EFFECTIVES, en millisecondes. Les valeurs par defaut sont celles du doc 06
 * ; {@code sdk/contract/resiliency-contract.json} les verifie.
 */
public record ResiliencyPolicy(long timeoutMillis, Retry retry, CircuitBreakerSpec circuitBreaker) {

    public static final long MAX_BACKOFF_MILLIS = 30_000;

    public enum Backoff { FIXED, EXPONENTIAL }

    public record Retry(int maxAttempts, Backoff backoff, long baseDelayMillis) {
        public Retry {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("retry.maxAttempts doit etre >= 1");
            }
            if (baseDelayMillis < 1) {
                throw new IllegalArgumentException("retry.baseDelayMillis doit etre >= 1");
            }
        }
    }

    public record CircuitBreakerSpec(int failureThreshold, long openDurationMillis) {
        public CircuitBreakerSpec {
            if (failureThreshold < 1) {
                throw new IllegalArgumentException("circuitBreaker.failureThreshold doit etre >= 1");
            }
            if (openDurationMillis < 1) {
                throw new IllegalArgumentException("circuitBreaker.openDurationMillis doit etre >= 1");
            }
        }
    }

    public ResiliencyPolicy {
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException("timeoutMillis doit etre >= 1");
        }
    }

    public static ResiliencyPolicy defaults() {
        return new ResiliencyPolicy(30_000, new Retry(1, Backoff.FIXED, 200), new CircuitBreakerSpec(5, 30_000));
    }

    /** Champs absents => valeurs par defaut (noyau plus ancien, ou bloc partiel). */
    @SuppressWarnings("unchecked")
    static ResiliencyPolicy fromJson(Object node) {
        if (!(node instanceof Map)) {
            return defaults();
        }
        Map<String, Object> json = (Map<String, Object>) node;
        ResiliencyPolicy d = defaults();
        Map<String, Object> retry = json.get("retry") instanceof Map ? (Map<String, Object>) json.get("retry") : Map.of();
        Map<String, Object> breaker = json.get("circuitBreaker") instanceof Map
                ? (Map<String, Object>) json.get("circuitBreaker") : Map.of();
        return new ResiliencyPolicy(
                number(json, "timeoutMillis", d.timeoutMillis()),
                new Retry((int) number(retry, "maxAttempts", d.retry().maxAttempts()),
                        retry.get("backoff") instanceof String b
                                ? Backoff.valueOf(b.toUpperCase(java.util.Locale.ROOT)) : d.retry().backoff(),
                        number(retry, "baseDelayMillis", d.retry().baseDelayMillis())),
                new CircuitBreakerSpec((int) number(breaker, "failureThreshold", d.circuitBreaker().failureThreshold()),
                        number(breaker, "openDurationMillis", d.circuitBreaker().openDurationMillis())));
    }

    private static long number(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        return value instanceof Number n ? n.longValue() : fallback;
    }
}
