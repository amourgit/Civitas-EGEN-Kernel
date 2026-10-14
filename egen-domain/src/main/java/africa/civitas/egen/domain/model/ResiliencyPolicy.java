package africa.civitas.egen.domain.model;

import java.time.Duration;

/**
 * Politique de resilience que les appelants appliquent a un service CIBLE
 * (voir docs/architecture/06-service-manifest.md, "Resilience declaree par la
 * cible", et docs/architecture/12-communication-fabric.md pour la semantique
 * normative). Declaree une seule fois par le service appele, appliquee
 * uniformement par tous ses appelants — jamais recodee chez chacun.
 *
 * <p>Ne contient que des seuils techniques : aucune regle metier, aucun
 * routage (garde-fou n1, docs/architecture/02-principes-fondamentaux.md).
 * Tout champ omis prend sa valeur par defaut ; un service sans bloc recoit
 * {@link #defaults()}, il n'existe donc pas d'appel inter-services non
 * protege.</p>
 */
public record ResiliencyPolicy(Duration timeout, RetrySpec retry, CircuitBreakerSpec circuitBreaker) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final int MAX_ATTEMPTS_LIMIT = 10;

    public enum Backoff { FIXED, EXPONENTIAL }

    /** @param maxAttempts tentatives TOTALES, la premiere incluse (1 = aucun retry) */
    public record RetrySpec(int maxAttempts, Backoff backoff, Duration baseDelay) {

        public static final int DEFAULT_MAX_ATTEMPTS = 1;
        public static final Duration DEFAULT_BASE_DELAY = Duration.ofMillis(200);

        public RetrySpec {
            if (maxAttempts < 1 || maxAttempts > MAX_ATTEMPTS_LIMIT) {
                throw new IllegalArgumentException("ResiliencyPolicy.retry.maxAttempts doit etre compris entre 1 et "
                        + MAX_ATTEMPTS_LIMIT + " (recu : " + maxAttempts + ")");
            }
            if (backoff == null) {
                backoff = Backoff.FIXED;
            }
            if (baseDelay == null) {
                baseDelay = DEFAULT_BASE_DELAY;
            }
            requirePositive(baseDelay, "retry.baseDelay");
        }

        public static RetrySpec defaults() {
            return new RetrySpec(DEFAULT_MAX_ATTEMPTS, Backoff.FIXED, DEFAULT_BASE_DELAY);
        }
    }

    /** @param failureThreshold echecs CONSECUTIFS qui ouvrent le circuit */
    public record CircuitBreakerSpec(int failureThreshold, Duration openDuration) {

        public static final int DEFAULT_FAILURE_THRESHOLD = 5;
        public static final Duration DEFAULT_OPEN_DURATION = Duration.ofSeconds(30);

        public CircuitBreakerSpec {
            if (failureThreshold < 1) {
                throw new IllegalArgumentException(
                        "ResiliencyPolicy.circuitBreaker.failureThreshold doit etre >= 1 (recu : " + failureThreshold + ")");
            }
            if (openDuration == null) {
                openDuration = DEFAULT_OPEN_DURATION;
            }
            requirePositive(openDuration, "circuitBreaker.openDuration");
        }

        public static CircuitBreakerSpec defaults() {
            return new CircuitBreakerSpec(DEFAULT_FAILURE_THRESHOLD, DEFAULT_OPEN_DURATION);
        }
    }

    public ResiliencyPolicy {
        if (timeout == null) {
            timeout = DEFAULT_TIMEOUT;
        }
        requirePositive(timeout, "timeout");
        if (retry == null) {
            retry = RetrySpec.defaults();
        }
        if (circuitBreaker == null) {
            circuitBreaker = CircuitBreakerSpec.defaults();
        }
    }

    public static ResiliencyPolicy defaults() {
        return new ResiliencyPolicy(null, null, null);
    }

    private static void requirePositive(Duration duration, String field) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("ResiliencyPolicy." + field + " doit etre strictement positif");
        }
    }
}
