package africa.civitas.egen.api.mapper;

import africa.civitas.egen.api.dto.ResiliencyDto;
import africa.civitas.egen.api.dto.ResiliencyViewDto;
import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ResiliencyPolicy.Backoff;
import africa.civitas.egen.domain.model.ResiliencyPolicy.CircuitBreakerSpec;
import africa.civitas.egen.domain.model.ResiliencyPolicy.RetrySpec;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DTO &lt;-&gt; domaine pour le bloc {@code resiliency} (voir
 * docs/architecture/06-service-manifest.md). Comme les autres mappers, ne
 * traduit que la forme : bornes et defauts sont ceux de
 * {@link ResiliencyPolicy}, qui reste seul juge de la validite.
 */
public final class ResiliencyMapper {

    private static final Pattern DURATION = Pattern.compile("^(\\d+)(ms|s|m|h)$");

    private ResiliencyMapper() {
    }

    /** @return {@code null} si le bloc est absent (le ServiceManifest applique alors les defauts) */
    public static ResiliencyPolicy toDomain(ResiliencyDto dto) {
        if (dto == null) {
            return null;
        }
        Duration timeout = dto.timeout == null ? null : parseDuration(dto.timeout, "resiliency.timeout");
        RetrySpec retry = null;
        if (dto.retry != null) {
            retry = new RetrySpec(
                    dto.retry.maxAttempts == null ? RetrySpec.DEFAULT_MAX_ATTEMPTS : dto.retry.maxAttempts,
                    dto.retry.backoff == null ? null : parseBackoff(dto.retry.backoff),
                    dto.retry.baseDelay == null ? null : parseDuration(dto.retry.baseDelay, "resiliency.retry.baseDelay"));
        }
        CircuitBreakerSpec breaker = null;
        if (dto.circuitBreaker != null) {
            breaker = new CircuitBreakerSpec(
                    dto.circuitBreaker.failureThreshold == null
                            ? CircuitBreakerSpec.DEFAULT_FAILURE_THRESHOLD : dto.circuitBreaker.failureThreshold,
                    dto.circuitBreaker.openDuration == null ? null
                            : parseDuration(dto.circuitBreaker.openDuration, "resiliency.circuitBreaker.openDuration"));
        }
        return new ResiliencyPolicy(timeout, retry, breaker);
    }

    /** Un entier suivi de {@code ms}, {@code s}, {@code m} ou {@code h} (docs/architecture/06). */
    static Duration parseDuration(String raw, String fieldName) {
        Matcher matcher = DURATION.matcher(raw == null ? "" : raw.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("ServiceManifest." + fieldName + " invalide : \"" + raw
                    + "\" — attendu un entier suivi de ms, s, m ou h (ex. \"200ms\", \"10s\")");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            default -> Duration.ofHours(amount);
        };
    }

    private static Backoff parseBackoff(String raw) {
        try {
            return Backoff.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ServiceManifest.resiliency.retry.backoff invalide : \"" + raw
                    + "\" — attendu fixed ou exponential", e);
        }
    }

    public static ResiliencyViewDto toView(ResiliencyPolicy policy) {
        ResiliencyViewDto view = new ResiliencyViewDto();
        view.timeoutMillis = policy.timeout().toMillis();
        view.retry = new ResiliencyViewDto.Retry();
        view.retry.maxAttempts = policy.retry().maxAttempts();
        view.retry.backoff = policy.retry().backoff().name().toLowerCase(Locale.ROOT);
        view.retry.baseDelayMillis = policy.retry().baseDelay().toMillis();
        view.circuitBreaker = new ResiliencyViewDto.CircuitBreaker();
        view.circuitBreaker.failureThreshold = policy.circuitBreaker().failureThreshold();
        view.circuitBreaker.openDurationMillis = policy.circuitBreaker().openDuration().toMillis();
        return view;
    }
}
