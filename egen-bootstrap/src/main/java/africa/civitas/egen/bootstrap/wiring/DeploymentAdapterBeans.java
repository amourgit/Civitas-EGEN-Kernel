package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.nomad.NomadDeploymentAdapter;
import africa.civitas.egen.application.observability.ObservedDeploymentPort;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Optional;

/**
 * Seul point du Kernel qui sait que le DeploymentPort est, en V1, implemente
 * par Nomad (voir docs/architecture/07-ports-et-adapters.md). Remplacer ou
 * ajouter un second Deployment Adapter (garde-fou n6,
 * docs/architecture/02-principes-fondamentaux.md ; voir aussi
 * docs/architecture/19-feuille-de-route.md, Phase 6) se fait en changeant
 * uniquement cette methode. Le bean expose est decore par
 * {@link ObservedDeploymentPort} (metriques generiques d'appel d'adapter,
 * voir docs/architecture/15-observabilite.md) — la reconciliation et les
 * use cases continuent de ne voir qu'un {@link DeploymentPort} ordinaire.
 */
@ApplicationScoped
public class DeploymentAdapterBeans {

    @Inject
    ObservabilityPort observabilityPort;

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
            @ConfigProperty(name = "egen.nomad.address", defaultValue = "http://localhost:4646")
            String nomadAddress,
            @ConfigProperty(name = "egen.nomad.token")
            Optional<String> nomadToken) {
        NomadDeploymentAdapter adapter = new NomadDeploymentAdapter(URI.create(nomadAddress), nomadToken.orElse(null));
        return new ObservedDeploymentPort(adapter, observabilityPort, "nomad");
    }
}
