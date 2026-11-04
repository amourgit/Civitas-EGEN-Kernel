package africa.civitas.egen.application.usecase;

import java.util.Set;

/**
 * {@code deployment.adapter} du manifeste désigne un adapter qui n'est pas activé dans ce Kernel
 * (docs/architecture/06-service-manifest.md, validation, étape 3). Une {@link IllegalArgumentException} :
 * la requête est invalide, l'API répond 400.
 */
public final class UnknownDeploymentAdapterException extends IllegalArgumentException {

    public UnknownDeploymentAdapterException(String adapter, Set<String> enabled) {
        super("deployment.adapter \"" + adapter + "\" n'est pas activé dans ce Kernel ; adapters activés : "
                + String.join(", ", enabled));
    }
}
