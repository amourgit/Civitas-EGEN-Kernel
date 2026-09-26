package africa.civitas.egen.application.port;

import java.util.Map;

/**
 * Une observation de mesure continue (duree, taille...) — voir
 * docs/architecture/15-observabilite.md, ex. "egen_reconcile_duration_seconds".
 * Les compteurs monotones ("_total") passent par
 * {@link ObservabilityPort#incrementCounter} plutot que par cette classe.
 */
public record MetricSample(String name, double value, Map<String, String> attributes) {

    public MetricSample {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MetricSample.name ne peut pas etre vide");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
