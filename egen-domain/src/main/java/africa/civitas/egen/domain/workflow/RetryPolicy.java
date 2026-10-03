package africa.civitas.egen.domain.workflow;

/**
 * Politique de retry d'une {@link Step} (voir
 * docs/architecture/11-moteur-de-workflow.md, exemple YAML :
 * {@code retry: { maxAttempts: 3, backoff: exponential }}).
 */
public record RetryPolicy(int maxAttempts, BackoffStrategy backoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("RetryPolicy.maxAttempts doit etre >= 1 (1 = aucun retry)");
        }
        if (backoff == null) {
            throw new IllegalArgumentException("RetryPolicy.backoff est obligatoire");
        }
    }

    /** Aucun retry : une seule tentative, backoff sans effet. */
    public static RetryPolicy none() {
        return new RetryPolicy(1, BackoffStrategy.FIXED);
    }

    public enum BackoffStrategy {
        FIXED,
        EXPONENTIAL
    }
}
