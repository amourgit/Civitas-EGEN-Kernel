package africa.civitas.egen.application.resilience;

import africa.civitas.egen.domain.model.ResiliencyPolicy.CircuitBreakerSpec;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Circuit breaker d'UN service cible — machine a etats normative de
 * docs/architecture/12-communication-fabric.md ("Semantique normative de la
 * resilience"), rejouee a l'identique par le SDK Java et le SDK Python via
 * {@code sdk/contract/resiliency-contract.json}.
 *
 * <pre>
 * CLOSED --(failureThreshold echecs consecutifs)--&gt; OPEN
 * OPEN --(openDuration ecoulee)--&gt; HALF_OPEN (UN seul essai admis)
 * HALF_OPEN --succes--&gt; CLOSED     HALF_OPEN --echec--&gt; OPEN (nouvelle fenetre)
 * </pre>
 *
 * <p>Protocole : {@link #tryAcquire()} avant l'appel ; si {@code true},
 * exactement un {@link #recordSuccess()} ou {@link #recordFailure()} apres.
 * Un rejet ne compte pas comme un echec et ne prolonge pas la fenetre
 * d'ouverture. Un resultat enregistre alors que le circuit est OPEN (appel
 * contourne, ex. une compensation) ne change pas son etat.</p>
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final CircuitBreakerSpec spec;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;
    private boolean probeInFlight;

    public CircuitBreaker(CircuitBreakerSpec spec, Clock clock) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CircuitBreakerSpec spec() {
        return spec;
    }

    /** @return {@code false} si l'appel doit etre rejete SANS aucun acces reseau */
    public synchronized boolean tryAcquire() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (!clock.instant().isBefore(openedAt.plus(spec.openDuration()))) {
                    state = State.HALF_OPEN;
                    probeInFlight = true;
                    return true;
                }
                return false;
            default: // HALF_OPEN
                if (probeInFlight) {
                    return false;
                }
                probeInFlight = true;
                return true;
        }
    }

    public synchronized void recordSuccess() {
        if (state == State.OPEN) {
            return;
        }
        consecutiveFailures = 0;
        probeInFlight = false;
        state = State.CLOSED;
    }

    public synchronized void recordFailure() {
        if (state == State.OPEN) {
            return;
        }
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= spec.failureThreshold()) {
            state = State.OPEN;
            openedAt = clock.instant();
            probeInFlight = false;
        }
    }

    /** Etat interne courant — l'ouverture n'expire qu'au prochain {@link #tryAcquire()}. */
    public synchronized State state() {
        return state;
    }
}
