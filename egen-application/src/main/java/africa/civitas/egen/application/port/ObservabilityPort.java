package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.Map;

/**
 * Port secondaire — generation de telemetrie (OpenTelemetry en V1, voir
 * docs/architecture/07-ports-et-adapters.md, "Observability Port"). Le
 * backend de visualisation (Prometheus/Grafana, Jaeger/Tempo) est un choix
 * d'infrastructure decouple par ce port — EGEN genere la telemetrie au
 * format OTLP, peu importe qui la consomme ensuite (voir
 * docs/architecture/15-observabilite.md).
 */
public interface ObservabilityPort {

    /** Span racine ou enfant — passer {@code parent} a null pour un span racine. */
    TraceSpan startSpan(String operationName, TraceContext parent);

    /** Mesure continue (duree, taille...) — voir {@link MetricSample}. */
    void recordDuration(MetricSample sample);

    /** Compteur monotone (ex. "egen_reconcile_failures_total"). */
    void incrementCounter(String name, Map<String, String> attributes);

    void recordEvent(ReconciliationEvent event);

    void reportCondition(ServiceId id, Condition condition);
}
