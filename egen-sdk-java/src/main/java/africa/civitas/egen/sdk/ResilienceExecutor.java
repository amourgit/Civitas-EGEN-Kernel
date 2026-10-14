package africa.civitas.egen.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Applique retry + circuit breaker autour d'une fonction d'envoi — la
 * semantique NORMATIVE de docs/architecture/12 (classification, backoff,
 * circuit), rejouee par {@code sdk/contract/resiliency-contract.json}.
 *
 * <p>Le timeout par tentative est applique par la fonction d'envoi (elle seule
 * parle au reseau) ; ici on ne voit que son resultat.</p>
 */
final class ResilienceExecutor {

    /** @param last dernier resultat obtenu ({@code null} si l'appel a ete rejete d'emblee) */
    record Result(Outcome last, boolean rejected, int attemptsMade, List<Long> sleepsMillis) {

        String render() {
            return rejected ? "rejected:circuit_open" : last.render();
        }
    }

    private final ResiliencyPolicy.Retry retry;
    private final CircuitBreaker breaker;
    private final Sleeper sleeper;

    ResilienceExecutor(ResiliencyPolicy.Retry retry, CircuitBreaker breaker, Sleeper sleeper) {
        this.retry = retry;
        this.breaker = breaker;
        this.sleeper = sleeper;
    }

    /** @param send recoit l'indice (0, 1, ...) de la tentative et la realise */
    Result run(IntFunction<Outcome> send) throws InterruptedException {
        List<Long> sleeps = new ArrayList<>();
        Outcome last = null;
        int attempts = 0;
        while (true) {
            if (!breaker.tryAcquire()) {
                // Rejete d'emblee, ou circuit ouvert par un autre appelant entre deux tentatives.
                return new Result(last, last == null, attempts, sleeps);
            }
            Outcome outcome = send.apply(attempts);
            attempts++;
            last = outcome;
            if (outcome.isRetryableFailure()) {
                breaker.recordFailure();
            } else {
                breaker.recordSuccess(); // succes, ou reponse definitive 4xx : le service a repondu
            }
            if (!outcome.isRetryableFailure()
                    || attempts >= retry.maxAttempts()
                    || breaker.state() == CircuitBreaker.State.OPEN) {
                return new Result(last, false, attempts, sleeps);
            }
            long delay = backoff(attempts);
            sleeps.add(delay);
            sleeper.sleep(delay);
        }
    }

    /** Attente apres la n-ieme tentative echouee : fixed = base ; exponential = base x 2^(n-1), plafonnee. */
    long backoff(int failedAttempts) {
        long delay = retry.backoff() == ResiliencyPolicy.Backoff.FIXED
                ? retry.baseDelayMillis()
                : retry.baseDelayMillis() * (1L << Math.min(failedAttempts - 1, 20));
        return Math.min(delay, ResiliencyPolicy.MAX_BACKOFF_MILLIS);
    }
}
