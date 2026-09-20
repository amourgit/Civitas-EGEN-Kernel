package africa.civitas.egen.domain.model;

/**
 * Le contrat technique declare par un service autonome (aggregate root du
 * domaine EGEN). Version minimale (id, version, runtime, deployment) — voir
 * docs/architecture/06-service-manifest.md pour le schema complet cible ;
 * les champs network/health/dependencies/events/configuration/lifecycle
 * rejoignent cette classe au fil des phases qui en ont besoin
 * (docs/architecture/19-feuille-de-route.md), jamais par anticipation.
 *
 * <p>Chaque invariant est applique ici, a la construction — jamais dans une
 * couche de validation separee qui pourrait etre contournee.</p>
 */
public record ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                               DeploymentSpec deployment) {

    public ServiceManifest {
        if (id == null) {
            throw new IllegalArgumentException("ServiceManifest.id est obligatoire");
        }
        if (version == null) {
            throw new IllegalArgumentException("ServiceManifest.version est obligatoire");
        }
        if (runtime == null) {
            throw new IllegalArgumentException("ServiceManifest.runtime est obligatoire");
        }
        if (deployment == null) {
            throw new IllegalArgumentException("ServiceManifest.deployment est obligatoire");
        }
    }
}
