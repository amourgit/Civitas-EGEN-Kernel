package africa.civitas.egen.application.resilience;

import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ServiceId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Evite un aller-retour Registry (donc PostgreSQL) par appel HTTP : la politique
 * d'une cible change rarement. Un manifeste redeclare est pris en compte au
 * plus tard apres {@code ttl}.
 */
public final class CachingResiliencyPolicyProvider implements ResiliencyPolicyProvider {

    private record Entry(ResiliencyPolicy policy, Instant expiresAt) {
    }

    private final ResiliencyPolicyProvider delegate;
    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentMap<String, Entry> cache = new ConcurrentHashMap<>();

    public CachingResiliencyPolicyProvider(ResiliencyPolicyProvider delegate, Clock clock, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
    }

    @Override
    public ResiliencyPolicy policyFor(ServiceId target) {
        Instant now = clock.instant();
        Entry entry = cache.get(target.value());
        if (entry != null && now.isBefore(entry.expiresAt())) {
            return entry.policy();
        }
        ResiliencyPolicy fresh = delegate.policyFor(target);
        cache.put(target.value(), new Entry(fresh, now.plus(ttl)));
        return fresh;
    }
}
