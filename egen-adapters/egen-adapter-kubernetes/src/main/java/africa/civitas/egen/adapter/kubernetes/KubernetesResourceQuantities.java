package africa.civitas.egen.adapter.kubernetes;

import africa.civitas.egen.application.port.DeploymentException;

import java.util.regex.Pattern;

/**
 * Validation des quantites de ressources d'un {@code DeploymentSpec}. Contrairement a
 * l'adapter Nomad, aucune conversion : {@code "500m"} et {@code "512Mi"} sont deja des
 * quantites Kubernetes et sont transmises telles quelles. Une valeur mal formee est refusee
 * ici, avant tout appel reseau (voir docs/architecture/07-ports-et-adapters.md,
 * « Kubernetes Adapter », « Objets crees »).
 */
final class KubernetesResourceQuantities {

    /** Entier, optionnellement suffixe de « m » (milli-coeur). */
    private static final Pattern CPU = Pattern.compile("^\\d+m?$");
    /** Entier, optionnellement suffixe de Ki, Mi, Gi ou Ti. */
    private static final Pattern MEMORY = Pattern.compile("^\\d+(Ki|Mi|Gi|Ti)?$");

    private KubernetesResourceQuantities() {
    }

    static String cpu(String cpu) {
        String value = cpu.trim();
        if (!CPU.matcher(value).matches()) {
            throw new DeploymentException("DeploymentSpec.cpu invalide pour l'adapter Kubernetes : \""
                    + cpu + "\" — attendu un entier optionnellement suffixe de \"m\" (ex. \"500m\")");
        }
        return value;
    }

    static String memory(String memory) {
        String value = memory.trim();
        if (!MEMORY.matcher(value).matches()) {
            throw new DeploymentException("DeploymentSpec.memory invalide pour l'adapter Kubernetes : \""
                    + memory + "\" — attendu un entier optionnellement suffixe de Ki, Mi, Gi ou Ti"
                    + " (ex. \"512Mi\")");
        }
        return value;
    }
}
