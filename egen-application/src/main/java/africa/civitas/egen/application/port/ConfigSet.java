package africa.civitas.egen.application.port;

import java.util.Map;

/** Paires cle/valeur typees par le manifeste — jamais leur signification metier (voir 07.4). */
public record ConfigSet(Map<String, String> values) {

    public ConfigSet {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
