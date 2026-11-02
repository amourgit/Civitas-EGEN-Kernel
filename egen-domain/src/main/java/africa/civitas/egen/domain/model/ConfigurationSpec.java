package africa.civitas.egen.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /** Regles de refus d'une publication de configuration (docs 13, « Publication de configuration »). */
    public enum Rule {
        /** La cle n'est pas dans {@code configuration.required} : le service ne l'attend pas. */
        UNKNOWN_KEY,
        /** La cle est une reference de secret : un secret n'est jamais publie en valeur. */
        SECRET_NOT_PUBLISHABLE,
        /** La valeur n'a pas le type declare par le manifeste. */
        TYPE_MISMATCH,
        /** La cle n'a pas de valeur par defaut et n'est pas fournie. */
        MISSING_REQUIRED_KEY
    }

    /**
     * Une violation : la cle fautive et la regle enfreinte. Ne porte jamais la valeur refusee — une
     * erreur d'API ou un journal ne doivent pas pouvoir divulguer une valeur de configuration (14).
     */
    public record Violation(String key, Rule rule, String message) {
    }

    /**
     * Valide un jeu complet de valeurs publie pour ce service. Remplacement complet : les valeurs par
     * defaut declarees restent applicables aux cles absentes, toute cle SANS defaut doit donc etre
     * fournie. Retourne TOUTES les violations (jamais la premiere seule), triees par cle puis par regle ;
     * liste vide si la publication est acceptable.
     */
    public List<Violation> validatePublication(Map<String, String> published) {
        List<Violation> violations = new ArrayList<>();
        Set<String> secretKeys = new HashSet<>();
        for (SecretReferenceSpec ref : secretsRefs) {
            secretKeys.add(ref.key());
        }
        Map<String, Key> declared = new java.util.HashMap<>();
        for (Key key : required) {
            declared.put(key.key(), key);
        }

        for (Map.Entry<String, String> entry : published.entrySet()) {
            String name = entry.getKey();
            Key key = declared.get(name);
            if (secretKeys.contains(name)) {
                violations.add(new Violation(name, Rule.SECRET_NOT_PUBLISHABLE,
                        "est une reference de secret : un secret n'est jamais publie en valeur"));
            } else if (key == null) {
                violations.add(new Violation(name, Rule.UNKNOWN_KEY,
                        "n'est pas declaree dans configuration.required du manifeste"));
            } else if (!key.accepts(entry.getValue())) {
                violations.add(new Violation(name, Rule.TYPE_MISMATCH, "doit etre de type " + key.type()));
            }
        }
        for (Key key : required) {
            if (key.defaultValue() == null && !published.containsKey(key.key())) {
                violations.add(new Violation(key.key(), Rule.MISSING_REQUIRED_KEY,
                        "n'a pas de valeur par defaut et doit etre fournie"));
            }
        }
        violations.sort(Comparator.comparing(Violation::key).thenComparing(Violation::rule));
        return List.copyOf(violations);
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
