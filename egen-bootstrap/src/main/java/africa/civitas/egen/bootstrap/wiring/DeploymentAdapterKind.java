package africa.civitas.egen.bootstrap.wiring;

import java.util.Arrays;
import java.util.Locale;

/**
 * Le Deployment Adapter actif du Kernel, choisi par la propriete {@code egen.deployment.adapter}
 * (voir docs/architecture/07-ports-et-adapters.md, « Kubernetes Adapter », « Selection »).
 * Un Kernel utilise un seul Deployment Adapter a la fois. Classe volontairement sans aucun import
 * Quarkus ni CDI : la regle de selection se teste sans conteneur.
 */
enum DeploymentAdapterKind {
    NOMAD("nomad"),
    KUBERNETES("kubernetes");

    /** Valeur de la propriete quand elle n'est pas renseignee. */
    static final String DEFAULT = "nomad";

    private final String propertyValue;

    DeploymentAdapterKind(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    String propertyValue() {
        return propertyValue;
    }

    /** @throws IllegalStateException si la valeur n'est pas l'un des adapters connus : le demarrage doit echouer. */
    static DeploymentAdapterKind parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (DeploymentAdapterKind kind : values()) {
            if (kind.propertyValue.equals(value)) {
                return kind;
            }
        }
        String allowed = String.join(", ", Arrays.stream(values()).map(DeploymentAdapterKind::propertyValue).toList());
        throw new IllegalStateException("egen.deployment.adapter = \"" + raw
                + "\" est inconnu — valeurs permises : " + allowed);
    }
}
