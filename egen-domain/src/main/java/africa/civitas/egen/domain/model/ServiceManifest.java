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
                               OwnerTeam ownerTeam, ResiliencyPolicy resiliency,
                               EventSpec events, ConfigurationSpec configuration,
                               List<ServiceInterface> interfaces, List<ResourceReference> resources,
                               ManifestMetadata metadata) {

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
        if (resiliency == null) {
            resiliency = ResiliencyPolicy.defaults();
        }
        if (events == null) {
            events = EventSpec.none();
        }
        if (configuration == null) {
            configuration = ConfigurationSpec.none();
        }
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
        resources = resources == null ? List.of() : List.copyOf(resources);
        if (metadata == null) {
            metadata = ManifestMetadata.none();
        }
        java.util.Set<String> resourceNames = new java.util.HashSet<>();
        for (ResourceReference resource : resources) {
            if (!resourceNames.add(resource.name())) {
                throw new IllegalArgumentException(
                        "dependencies.resources : ressource declaree deux fois : \"" + resource.name() + "\"");
            }
        }
        if (ownerTeam == null) {
            ownerTeam = OwnerTeam.unassigned();
        }
    }

    /** Convenience historique : sans {@code metadata.labels/annotations} (docs/architecture/06). */
    public ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                            DeploymentSpec deployment, HealthSpec health, LifecyclePolicy lifecycle,
                            List<Dependency> dependencies, OwnerTeam ownerTeam, ResiliencyPolicy resiliency,
                            EventSpec events, ConfigurationSpec configuration, List<ServiceInterface> interfaces,
                            List<ResourceReference> resources) {
        this(id, version, runtime, deployment, health, lifecycle, dependencies, ownerTeam, resiliency,
                events, configuration, interfaces, resources, null);
    }

    /**
     * Convenience historique : sans {@code dependencies.resources}, referencees mais jamais gerees en V1
     * (docs/architecture/06 et 20).
     */
    public ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                            DeploymentSpec deployment, HealthSpec health, LifecyclePolicy lifecycle,
                            List<Dependency> dependencies, OwnerTeam ownerTeam, ResiliencyPolicy resiliency,
                            EventSpec events, ConfigurationSpec configuration, List<ServiceInterface> interfaces) {
        this(id, version, runtime, deployment, health, lifecycle, dependencies, ownerTeam, resiliency,
                events, configuration, interfaces, null, null);
    }

    /**
     * Convenience historique (Phase 5) : sans les blocs {@code events},
     * {@code configuration} et {@code interfaces} (docs/architecture/06 et 21.3),
     * tous trois optionnels et additifs.
     */
    public ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                            DeploymentSpec deployment, HealthSpec health, LifecyclePolicy lifecycle,
                            List<Dependency> dependencies, OwnerTeam ownerTeam, ResiliencyPolicy resiliency) {
        this(id, version, runtime, deployment, health, lifecycle, dependencies, ownerTeam, resiliency,
                null, null, null, null, null);
    }

    /**
     * Convenience historique (Phase 4) : sans bloc {@code resiliency} — recoit
     * {@link ResiliencyPolicy#defaults()} (docs/architecture/06-service-manifest.md).
     */
    public ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                            DeploymentSpec deployment, HealthSpec health, LifecyclePolicy lifecycle,
                            List<Dependency> dependencies, OwnerTeam ownerTeam) {
        this(id, version, runtime, deployment, health, lifecycle, dependencies, ownerTeam, null);
    }

    /** Convenience historique (Phase 1-3) : delegue avec {@link OwnerTeam#unassigned()}. */
    public ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                            DeploymentSpec deployment, HealthSpec health, LifecyclePolicy lifecycle,
                            List<Dependency> dependencies) {
        this(id, version, runtime, deployment, health, lifecycle, dependencies, null, null);
    }

    /** Convenience : construit un manifeste sans dependance declaree ni equipe assignee. */
    public ServiceManifest(ServiceId id, ServiceVersion version, ServiceRuntime runtime,
                            DeploymentSpec deployment, HealthSpec health, LifecyclePolicy lifecycle) {
        this(id, version, runtime, deployment, health, lifecycle, List.of(), null, null);
    }
}
