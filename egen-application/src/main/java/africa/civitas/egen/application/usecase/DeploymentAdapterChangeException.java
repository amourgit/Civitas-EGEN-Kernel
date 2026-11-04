package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.ServiceId;

/**
 * Le manifeste déclaré change le {@code deployment.adapter} d'un service qui existe déjà. Refusé : les
 * instances de l'ancien moteur resteraient sans pilote (docs/architecture/07-ports-et-adapters.md,
 * « Sélection et routage », étape 2). L'API répond 409.
 */
public final class DeploymentAdapterChangeException extends IllegalStateException {

    public DeploymentAdapterChangeException(ServiceId id, String current, String requested) {
        super("Le service \"" + id + "\" s'exécute sur l'adapter \"" + current + "\" ; son adapter ne peut pas "
                + "passer à \"" + requested + "\". Pour changer de moteur, déclarer un nouveau service puis "
                + "arrêter celui-ci.");
    }
}
