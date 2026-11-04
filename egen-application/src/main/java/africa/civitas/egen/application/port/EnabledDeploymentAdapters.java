package africa.civitas.egen.application.port;

import java.util.Set;
import java.util.TreeSet;

/**
 * La liste des Deployment Adapters <b>activés</b> dans ce Kernel, par nom (voir
 * docs/architecture/07-ports-et-adapters.md, « Sélection et routage »). Le use case de déclaration la
 * consulte pour refuser un manifeste dont {@code deployment.adapter} désigne un adapter absent
 * (docs/architecture/06-service-manifest.md, validation, étape 3).
 */
public interface EnabledDeploymentAdapters {

    boolean isEnabled(String adapterName);

    /** Noms activés, triés, pour les messages d'erreur. */
    Set<String> enabledNames();

    static EnabledDeploymentAdapters of(Set<String> names) {
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("Au moins un Deployment Adapter doit être activé");
        }
        Set<String> sorted = java.util.Collections.unmodifiableSet(new TreeSet<>(names));
        return new EnabledDeploymentAdapters() {
            @Override
            public boolean isEnabled(String adapterName) {
                return sorted.contains(adapterName);
            }

            @Override
            public Set<String> enabledNames() {
                return sorted;
            }
        };
    }

    /**
     * Liste qui accepte tout nom. Réservé aux tests et aux assemblages à un seul faux port : le
     * câblage de production passe toujours par {@link #of(Set)}.
     */
    static EnabledDeploymentAdapters acceptingAny() {
        return new EnabledDeploymentAdapters() {
            @Override
            public boolean isEnabled(String adapterName) {
                return true;
            }

            @Override
            public Set<String> enabledNames() {
                return Set.of();
            }
        };
    }
}
