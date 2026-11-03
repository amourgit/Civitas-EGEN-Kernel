package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.kubernetes.KubernetesConfig;
import africa.civitas.egen.adapter.kubernetes.KubernetesDeploymentAdapter;
import africa.civitas.egen.adapter.nomad.NomadDeploymentAdapter;
import africa.civitas.egen.application.observability.ObservedDeploymentPort;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Seul point du Kernel qui sait quel moteur implemente le DeploymentPort : Nomad (defaut) ou
 * Kubernetes, selon {@code egen.deployment.adapter} (voir
 * docs/architecture/07-ports-et-adapters.md, « Kubernetes Adapter » ; garde-fou n6,
 * docs/architecture/02-principes-fondamentaux.md ; docs/architecture/19-feuille-de-route.md,
 * Phase 6). Ajouter ou remplacer un Deployment Adapter ne modifie que cette classe. Le bean expose
 * est decore par {@link ObservedDeploymentPort} (metriques generiques d'appel d'adapter, voir
 * docs/architecture/15-observabilite.md) — la reconciliation et les use cases continuent de ne voir
 * qu'un {@link DeploymentPort} ordinaire.
 */
@ApplicationScoped
public class DeploymentAdapterBeans {

    @Inject
    ObservabilityPort observabilityPort;

    /**
     * Une valeur inconnue de {@code egen.deployment.adapter} doit faire echouer le DEMARRAGE, pas la
     * premiere reconciliation : le producteur ci-dessous est paresseux (bean {@code @ApplicationScoped}).
     */
    void onStart(@Observes StartupEvent event,
                 @ConfigProperty(name = "egen.deployment.adapter", defaultValue = DeploymentAdapterKind.DEFAULT)
                 String adapter) {
        DeploymentAdapterKind.parse(adapter);
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
            @ConfigProperty(name = "egen.deployment.adapter", defaultValue = DeploymentAdapterKind.DEFAULT)
            String adapter,
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
        DeploymentAdapterKind kind = DeploymentAdapterKind.parse(adapter);
        DeploymentPort delegate = switch (kind) {
            case NOMAD -> new NomadDeploymentAdapter(URI.create(nomadAddress), nomadToken.orElse(null));
            case KUBERNETES -> new KubernetesDeploymentAdapter(new KubernetesConfig(
                    URI.create(kubernetesAddress), kubernetesNamespace, kubernetesToken.orElse(null),
                    kubernetesCaCertPath.map(Path::of).orElse(null), kubernetesContainerPort));
        };
        return new ObservedDeploymentPort(delegate, observabilityPort, kind.propertyValue());
    }
}
