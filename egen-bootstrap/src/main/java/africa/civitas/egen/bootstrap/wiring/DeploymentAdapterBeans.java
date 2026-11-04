package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.kubernetes.KubernetesConfig;
import africa.civitas.egen.adapter.kubernetes.KubernetesDeploymentAdapter;
import africa.civitas.egen.adapter.nomad.NomadDeploymentAdapter;
import africa.civitas.egen.application.observability.ObservedDeploymentPort;
import africa.civitas.egen.application.port.EnabledDeploymentAdapters;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.routing.RoutingDeploymentPort;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seul point du Kernel qui sait quels moteurs implémentent le DeploymentPort : Nomad (défaut) et/ou
 * Kubernetes, selon {@code egen.deployment.adapters} (voir docs/architecture/07-ports-et-adapters.md,
 * « Sélection et routage » ; garde-fou n6, docs/architecture/02-principes-fondamentaux.md ;
 * docs/architecture/19-feuille-de-route.md, Phase 6).
 *
 * <p>Chaque adapter activé est enveloppé séparément dans {@link ObservedDeploymentPort} (métriques par
 * adapter, voir docs/architecture/15-observabilite.md), puis le tout est confié à un
 * {@link RoutingDeploymentPort} : la réconciliation et les use cases continuent de ne voir qu'un
 * {@link DeploymentPort} ordinaire. Un adapter non activé n'est jamais construit.</p>
 */
@ApplicationScoped
public class DeploymentAdapterBeans {

    @Inject
    ObservabilityPort observabilityPort;

    @Inject
    RegistryStorePort registryStorePort;

    /**
     * Une liste invalide de {@code egen.deployment.adapters} doit faire échouer le DÉMARRAGE, pas la
     * première réconciliation : le producteur ci-dessous est paresseux (bean {@code @ApplicationScoped}).
     */
    void onStart(@Observes StartupEvent event,
                 @ConfigProperty(name = "egen.deployment.adapters", defaultValue = DeploymentAdapterKind.DEFAULT)
                 String adapters) {
        DeploymentAdapterKind.parseList(adapters);
    }

    /** Les adapters activés, lus par le use case de déclaration (docs/architecture/06, validation, étape 3). */
    @Produces
    @ApplicationScoped
    public EnabledDeploymentAdapters enabledDeploymentAdapters(
            @ConfigProperty(name = "egen.deployment.adapters", defaultValue = DeploymentAdapterKind.DEFAULT)
            String adapters) {
        Set<String> names = DeploymentAdapterKind.parseList(adapters).stream()
                .map(DeploymentAdapterKind::propertyValue)
                .collect(Collectors.toSet());
        return EnabledDeploymentAdapters.of(names);
    }

    @Produces
    @ApplicationScoped
    // Optional<String> comme type de parametre est ici le mode d'injection
    // officiel de MicroProfile Config pour une propriete SANS defaultValue
    // (voir MicroProfile Config, section "Optional Values") : ce n'est pas un
    // appel d'API ordinaire concu par nous, mais un point d'injection CDI
    // renseigne par le conteneur — l'argument habituel contre Optional en
    // parametre (ergonomie de l'appelant) ne s'applique pas ici.
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public DeploymentPort deploymentPort(
            @ConfigProperty(name = "egen.deployment.adapters", defaultValue = DeploymentAdapterKind.DEFAULT)
            String adapters,
            @ConfigProperty(name = "egen.nomad.address", defaultValue = "http://localhost:4646")
            String nomadAddress,
            @ConfigProperty(name = "egen.nomad.token")
            Optional<String> nomadToken,
            @ConfigProperty(name = "egen.kubernetes.address", defaultValue = "https://kubernetes.default.svc")
            String kubernetesAddress,
            @ConfigProperty(name = "egen.kubernetes.namespace", defaultValue = "egen")
            String kubernetesNamespace,
            @ConfigProperty(name = "egen.kubernetes.token")
            Optional<String> kubernetesToken,
            @ConfigProperty(name = "egen.kubernetes.ca-cert-path")
            Optional<String> kubernetesCaCertPath,
            @ConfigProperty(name = "egen.kubernetes.container-port", defaultValue = "8080")
            int kubernetesContainerPort) {
        Map<String, DeploymentPort> enabled = new LinkedHashMap<>();
        for (DeploymentAdapterKind kind : DeploymentAdapterKind.parseList(adapters)) {
            DeploymentPort adapter = switch (kind) {
                case NOMAD -> new NomadDeploymentAdapter(URI.create(nomadAddress), nomadToken.orElse(null));
                case KUBERNETES -> new KubernetesDeploymentAdapter(new KubernetesConfig(
                        URI.create(kubernetesAddress), kubernetesNamespace, kubernetesToken.orElse(null),
                        kubernetesCaCertPath.map(Path::of).orElse(null), kubernetesContainerPort));
            };
            enabled.put(kind.propertyValue(),
                    new ObservedDeploymentPort(adapter, observabilityPort, kind.propertyValue()));
        }
        return new RoutingDeploymentPort(enabled, registryStorePort);
    }
}
