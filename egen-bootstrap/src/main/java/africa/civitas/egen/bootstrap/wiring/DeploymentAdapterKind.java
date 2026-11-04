package africa.civitas.egen.bootstrap.wiring;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Un Deployment Adapter que ce Kernel sait activer, désigné par son nom dans la propriété
 * {@code egen.deployment.adapters} et dans {@code deployment.adapter} des manifestes (voir
 * docs/architecture/07-ports-et-adapters.md, « Sélection et routage »). Classe volontairement sans
 * aucun import Quarkus ni CDI : la règle de sélection se teste sans conteneur.
 */
enum DeploymentAdapterKind {
    NOMAD("nomad"),
    KUBERNETES("kubernetes");

    /** Valeur de la propriété quand elle n'est pas renseignée. */
    static final String DEFAULT = "nomad";

    private final String propertyValue;

    DeploymentAdapterKind(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    String propertyValue() {
        return propertyValue;
    }

    /**
     * Lit la liste, séparée par des virgules, des adapters activés. L'ordre donné est conservé.
     *
     * @throws IllegalStateException si la liste est vide, contient un nom inconnu ou un doublon : le
     *                               démarrage doit échouer
     */
    static Set<DeploymentAdapterKind> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("egen.deployment.adapters est vide — au moins un adapter doit être activé ; "
                    + "valeurs permises : " + allowed());
        }
        Set<DeploymentAdapterKind> enabled = new LinkedHashSet<>();
        for (String item : raw.split(",", -1)) {
            DeploymentAdapterKind kind = parse(item);
            if (!enabled.add(kind)) {
                throw new IllegalStateException("egen.deployment.adapters = \"" + raw + "\" cite deux fois \""
                        + kind.propertyValue + "\"");
            }
        }
        return enabled;
    }

    private static DeploymentAdapterKind parse(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (DeploymentAdapterKind kind : values()) {
            if (kind.propertyValue.equals(value)) {
                return kind;
            }
        }
        throw new IllegalStateException("egen.deployment.adapters contient \"" + raw.trim()
                + "\", qui est inconnu — valeurs permises : " + allowed());
    }

    private static String allowed() {
        return String.join(", ", Arrays.stream(values()).map(DeploymentAdapterKind::propertyValue).toList());
    }
}
