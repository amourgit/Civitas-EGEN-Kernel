package africa.civitas.egen.domain.model;

import java.util.List;

/**
 * Le contrat technique declare par un service autonome (aggregate root du
 * domaine EGEN). Champs Phase 1-3 (id, version, runtime, deployment,
 * health, lifecycle, dependencies) — voir
 * docs/architecture/06-service-manifest.md pour le schema complet cible ;
 * network/events/configuration rejoignent cette classe au fil des phases
 * qui en ont besoin (docs/architecture/19-feuille-de-route.md), jamais par
 * anticipation.
 *
 * <p>Chaque invariant est applique ici, a la construction — jamais dans une
 * couche de validation separee qui pourrait etre contournee.
 * {@code lifecycle} a une valeur par defaut
 * ({@link LifecyclePolicy#defaultPolicy()}) si omis ; {@code dependencies}
 * est vide si omis (aucune dependance n'est le cas normal, pas une
 * exception).</p>
 *
 * <p>{@code ownerTeam} (Phase 4, voir
 * docs/architecture/13-api-et-contrats.md et
 * docs/architecture/14-securite.md) a ete ajoute en derniere position, de
 * facon strictement additive : les deux constructeurs de convenance
 * historiques (6 et 7 arguments) restent valides tels quels et delivrent
 * {@link OwnerTeam#unassigned()} — aucun site d'appel existant n'a besoin
 * d'etre modifie (docs/architecture/13-api-et-contrats.md, "toute
 * evolution du schema ServiceManifest est retro-compatible en lecture").</p>
 */
public record ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                               DeploymentSpec deployment, HealthSpec health,
                               LifecyclePolicy lifecycle, List<Dependency> dependencies,
                               OwnerTeam ownerTeam) {

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
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        if (ownerTeam == null) {
            ownerTeam = OwnerTeam.unassigned();
        }
    }

    /** Convenience historique (Phase 1-3) : delegue avec {@link OwnerTeam#unassigned()}. */
    public ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                            DeploymentSpec deployment, HealthSpec health, LifecyclePolicy lifecycle,
                            List<Dependency> dependencies) {
        this(id, version, runtime, deployment, health, lifecycle, dependencies, null);
    }

    /** Convenience : construit un manifeste sans dependance declaree ni equipe assignee. */
    public ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                            DeploymentSpec deployment, HealthSpec health, LifecyclePolicy lifecycle) {
        this(id, version, runtime, deployment, health, lifecycle, List.of(), null);
    }
}
