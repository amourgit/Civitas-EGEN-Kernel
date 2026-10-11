package africa.civitas.egen.application.workflow;

import java.util.Map;
import java.util.TreeMap;

/**
 * Serialisation minimale d'une {@code Map<String,String>} en objet JSON, pour
 * le champ {@code data} d'un {@code TechnicalEvent} (l'adapter de messaging
 * l'exige en JSON valide). Ecrite a la main pour ne pas introduire de
 * bibliotheque JSON dans egen-application (garde-fou n3,
 * docs/architecture/02-principes-fondamentaux.md) ; cles triees pour une
 * sortie deterministe.
 */
final class JsonObjects {

    private JsonObjects() {
    }

    static String toJson(Map<String, String> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendString(json, entry.getKey());
            json.append(':');
            appendString(json, entry.getValue());
        }
        return json.append('}').toString();
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }
}
