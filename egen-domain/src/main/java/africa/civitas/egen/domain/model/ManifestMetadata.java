package africa.civitas.egen.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadonnees informatives du manifeste (voir docs/architecture/06-service-manifest.md,
 * {@code metadata.labels} et {@code metadata.annotations}). Conservees et
 * restituees telles quelles ; le Kernel ne s'en sert ni pour decider ni pour
 * router (garde-fou n1, docs/architecture/02-principes-fondamentaux.md).
 */
public record ManifestMetadata(Map<String, String> labels, Map<String, String> annotations) {

    public ManifestMetadata {
        labels = copy(labels, "metadata.labels");
        annotations = copy(annotations, "metadata.annotations");
    }

    public static ManifestMetadata none() {
        return new ManifestMetadata(null, null);
    }

    private static Map<String, String> copy(Map<String, String> source, String field) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException(field + " : cle vide");
                }
                copy.put(key, value == null ? "" : value);
            });
        }
        return Collections.unmodifiableMap(copy);
    }
}
