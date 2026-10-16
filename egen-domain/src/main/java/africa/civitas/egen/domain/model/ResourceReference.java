package africa.civitas.egen.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ressource externe nommee, non-service, dont depend le service (voir
 * docs/architecture/06-service-manifest.md, {@code dependencies.resources},
 * « inspire de Score »). <b>Referencee, pas geree en V1</b>
 * (docs/architecture/20-scenario-bout-en-bout.md, etape 2) : le Kernel la
 * stocke et la restitue, ne provisionne rien et n'interprete ni {@code type}
 * ni {@code params} — hors perimetre du Deployment Adapter.
 *
 * @param params parametres libres a valeurs scalaires (texte)
 */
public record ResourceReference(String name, String type, Map<String, String> params) {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$");

    public ResourceReference {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("dependencies.resources[].name invalide : \"" + name
                    + "\" — attendu des lettres, chiffres, '_', '.' ou '-'");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("dependencies.resources[" + name + "].type ne peut pas etre vide");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException("dependencies.resources[" + name + "].params : cle vide");
                }
                copy.put(key, value == null ? "" : value);
            });
        }
        params = java.util.Collections.unmodifiableMap(copy);
    }
}
