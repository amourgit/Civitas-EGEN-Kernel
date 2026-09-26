package africa.civitas.egen.application.port;

/**
 * Handle neutre vers un span actif — voir
 * docs/architecture/07-ports-et-adapters.md, "ObservabilityPort.startSpan".
 * {@link #close()} termine le span ; utiliser en try-with-resources.
 */
public interface TraceSpan extends AutoCloseable {

    /** Ce span en tant que parent pour un appel imbrique (voir {@link TraceContext}). */
    TraceContext context();

    void setAttribute(String key, String value);

    void recordError(Throwable error);

    @Override
    void close();
}
