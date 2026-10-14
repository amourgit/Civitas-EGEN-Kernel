package africa.civitas.egen.application.resilience;

import africa.civitas.egen.domain.model.ResiliencyPolicy.CircuitBreakerSpec;
import africa.civitas.egen.domain.model.ServiceId;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Un {@link CircuitBreaker} par service cible, partage par tous les appelants
 * du processus (docs/architecture/12 : "un par service cible et par processus
 * appelant"). Si le manifeste de la cible est redeclare avec d'autres seuils,
 * le circuit est recree (etat remis a CLOSED) au premier appel suivant.
 */
public final class ServiceCircuitBreakers {

    private final Clock clock;
    private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public ServiceCircuitBreakers(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CircuitBreaker forService(ServiceId service, CircuitBreakerSpec spec) {
        return breakers.compute(service.value(), (key, existing) ->
                existing != null && existing.spec().equals(spec) ? existing : new CircuitBreaker(spec, clock));
    }
}
