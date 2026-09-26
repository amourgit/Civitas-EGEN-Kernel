package africa.civitas.egen.application.port;

/**
 * Contexte de trace neutre — la chaine {@code traceparent} au format W3C
 * Trace Context standard (voir
 * docs/architecture/07-ports-et-adapters.md, "Observability Port" : "le
 * Kernel doit propager le contexte de trace (traceparent W3C)"). Ce format
 * texte standard est ce qui permet a ObservabilityPort de rester neutre :
 * aucun type d'un SDK de tracing (OpenTelemetry ou autre) ne fuit jamais
 * dans egen-application (garde-fou n2,
 * docs/architecture/02-principes-fondamentaux.md).
 */
public record TraceContext(String traceparent) {

    public TraceContext {
        if (traceparent == null || traceparent.isBlank()) {
            throw new IllegalArgumentException("TraceContext.traceparent ne peut pas etre vide");
        }
    }
}
