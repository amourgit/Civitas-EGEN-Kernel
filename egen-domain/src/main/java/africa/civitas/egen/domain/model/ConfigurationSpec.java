package africa.civitas.egen.domain.model;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Configuration SANS valeurs (voir docs/architecture/06-service-manifest.md,
 * bloc {@code configuration}) : quelles cles le service attend, et quelles
 * references de secrets il utilise. EGEN ne stocke JAMAIS un secret —
 * uniquement une reference (docs/architecture/07, "Secrets Port").
 *
 * <p>Le doc 06 ne fixe pas le vocabulaire des types ; il est ici ferme :
 * {@code string} (defaut), {@code boolean}, {@code integer}, {@code number}.</p>
 */
public record ConfigurationSpec(List<Key> required, List<SecretReferenceSpec> secretsRefs) {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]{0,127}$");
    public static final Set<String> TYPES = Set.of("string", "boolean", "integer", "number");

    /**
     * @param type {@code string}, {@code boolean}, {@code integer} ou {@code number} ({@code string} si omis)
     * @param defaultValue valeur par defaut, sous forme de texte ; {@code null} = pas de defaut,
     *                     la cle doit alors etre fournie par la configuration
     */
    public record Key(String key, String type, String defaultValue) {
        public Key {
            key = requireName(key, "configuration.required.key");
            type = type == null || type.isBlank() ? "string" : type.toLowerCase(Locale.ROOT);
            if (!TYPES.contains(type)) {
                throw new IllegalArgumentException("configuration.required[" + key + "].type invalide : \"" + type
                        + "\" — attendu string, boolean, integer ou number");
            }
            if (defaultValue != null && !conforms(type, defaultValue)) {
                throw new IllegalArgumentException("configuration.required[" + key + "].default \"" + defaultValue
                        + "\" n'est pas de type " + type);
            }
        }

        /** Une valeur donnee (defaut ou valeur resolue) est-elle du type declare ? */
        public boolean accepts(String value) {
            return value != null && conforms(type, value);
        }
    }

    public record SecretReferenceSpec(String key, String provider, String path) {
        public SecretReferenceSpec {
            key = requireName(key, "configuration.secretsRefs.key");
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("configuration.secretsRefs[" + key + "].provider ne peut pas etre vide");
            }
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("configuration.secretsRefs[" + key + "].path ne peut pas etre vide");
            }
        }
    }

    public ConfigurationSpec {
        required = required == null ? List.of() : List.copyOf(required);
        secretsRefs = secretsRefs == null ? List.of() : List.copyOf(secretsRefs);
        Set<String> names = new HashSet<>();
        for (Key key : required) {
            if (!names.add(key.key())) {
                throw new IllegalArgumentException("configuration : cle declaree deux fois : \"" + key.key() + "\"");
            }
        }
        for (SecretReferenceSpec ref : secretsRefs) {
            if (!names.add(ref.key())) {
                throw new IllegalArgumentException(
                        "configuration : cle declaree deux fois (configuration et secret) : \"" + ref.key() + "\"");
            }
        }
    }

    public static ConfigurationSpec none() {
        return new ConfigurationSpec(List.of(), List.of());
    }

    public boolean isEmpty() {
        return required.isEmpty() && secretsRefs.isEmpty();
    }

    private static String requireName(String name, String field) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(field + " invalide : \"" + name
                    + "\" — attendu une lettre ou '_' puis des lettres, chiffres, '_', '.' ou '-'");
        }
        return name;
    }

    private static boolean conforms(String type, String value) {
        return switch (type) {
            case "boolean" -> value.equals("true") || value.equals("false");
            case "integer" -> {
                try {
                    Long.parseLong(value);
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "number" -> {
                try {
                    Double.parseDouble(value);
                    yield !value.isBlank();
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            default -> true;
        };
    }
}
