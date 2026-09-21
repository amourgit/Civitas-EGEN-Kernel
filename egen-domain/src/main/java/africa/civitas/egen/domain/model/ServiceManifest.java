package africa.civitas.egen.domain.model;

/**
 * Le contrat technique declare par un service autonome (aggregate root du
 * domaine EGEN). Champs Phase 1-2 (id, version, runtime, deployment, health,
 * lifecycle) — voir docs/architecture/06-service-manifest.md pour le schema
 * complet cible ; network/dependencies/events/configuration rejoignent
 * cette classe au fil des phases qui en ont besoin
 * (docs/architecture/19-feuille-de-route.md), jamais par anticipation.
 *
 * <p>Chaque invariant est applique ici, a la construction — jamais dans une
 * couche de validation separee qui pourrait etre contournee.
 * {@code lifecycle} a une valeur par defaut
 * ({@link LifecyclePolicy#defaultPolicy()}) si omis.</p>
 */
public record ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                               DeploymentSpec deployment, HealthSpec health,
                               LifecyclePolicy lifecycle) {

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
        if (health == null) {
            throw new IllegalArgumentException("ServiceManifest.health est obligatoire");
        }
        if (lifecycle == null) {
            lifecycle = LifecyclePolicy.defaultPolicy();
        }
    }
}
