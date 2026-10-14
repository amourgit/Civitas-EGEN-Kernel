package africa.civitas.egen.sdk;

import java.util.function.LongSupplier;

/**
 * Circuit breaker d'UN service cible — machine a etats normative de
 * docs/architecture/12-communication-fabric.md, identique a celle du Kernel
 * (egen-application.resilience) et du SDK Python : tous trois rejouent
 * {@code sdk/contract/resiliency-contract.json}.
 *
 * <p>Protocole : {@link #tryAcquire()} avant l'appel ; si {@code true},
 * exactement un {@link #recordSuccess()} ou {@link #recordFailure()} apres.
 * Un rejet ne compte pas comme un echec et ne prolonge pas la fenetre
 * d'ouverture.</p>
 */
final class CircuitBreaker {

    enum State { CLOSED, OPEN, HALF_OPEN }

    private final ResiliencyPolicy.CircuitBreakerSpec spec;
    private final LongSupplier clockMillis;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAtMillis;
    private boolean probeInFlight;

    CircuitBreaker(ResiliencyPolicy.CircuitBreakerSpec spec, LongSupplier clockMillis) {
        this.spec = spec;
        this.clockMillis = clockMillis;
    }

    ResiliencyPolicy.CircuitBreakerSpec spec() {
        return spec;
    }

    /** @return {@code false} si l'appel doit etre rejete SANS aucun acces reseau */
    synchronized boolean tryAcquire() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (clockMillis.getAsLong() - openedAtMillis >= spec.openDurationMillis()) {
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

    synchronized void recordSuccess() {
        if (state == State.OPEN) {
            return;
        }
        consecutiveFailures = 0;
        probeInFlight = false;
        state = State.CLOSED;
    }

    synchronized void recordFailure() {
        if (state == State.OPEN) {
            return;
        }
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= spec.failureThreshold()) {
            state = State.OPEN;
            openedAtMillis = clockMillis.getAsLong();
            probeInFlight = false;
        }
    }

    /** Etat interne courant — l'ouverture n'expire qu'au prochain {@link #tryAcquire()}. */
    synchronized State state() {
        return state;
    }
}
