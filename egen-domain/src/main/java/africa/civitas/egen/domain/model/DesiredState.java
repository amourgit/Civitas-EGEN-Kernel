package africa.civitas.egen.domain.model;

/**
 * Etat desire persiste par EGEN — source de verite declarative (voir
 * docs/architecture/04-moteur-de-reconciliation.md, "Etat desire vs etat
 * observe"). {@code generation} est incremente a chaque modification du
 * manifeste par le RegistryStorePort au moment de la sauvegarde ; cette
 * classe transporte la generation courante, elle ne l'attribue pas
 * elle-meme (ce n'est pas au domaine de gerer un compteur partage entre
 * ecritures concurrentes — c'est le role du verrou optimiste du Registry,
 * voir docs/architecture/08-registry.md).
 */
public record DesiredState(ServiceManifest manifest, long generation,
                            TargetEnvironment targetEnvironment) {

    public DesiredState {
        if (manifest == null) {
            throw new IllegalArgumentException("DesiredState.manifest est obligatoire");
        }
        if (generation < 0) {
            throw new IllegalArgumentException("DesiredState.generation ne peut pas etre negative");
        }
        if (targetEnvironment == null) {
            throw new IllegalArgumentException("DesiredState.targetEnvironment est obligatoire");
        }
    }

    public ServiceId serviceId() {
        return manifest.id();
    }
}
