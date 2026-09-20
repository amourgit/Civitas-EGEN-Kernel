package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.nomad.NomadDeploymentAdapter;
import africa.civitas.egen.application.port.DeploymentPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Optional;

/**
 * Seul point du Kernel qui sait que le DeploymentPort est, en V1, implemente
 * par Nomad (voir docs/architecture/07-ports-et-adapters.md). Remplacer ou
 * ajouter un second Deployment Adapter (garde-fou n6,
 * docs/architecture/02-principes-fondamentaux.md ; voir aussi
 * docs/architecture/19-feuille-de-route.md, Phase 6) se fait en changeant
 * uniquement cette methode.
 */
@ApplicationScoped
public class DeploymentAdapterBeans {

    @Produces
    @ApplicationScoped
    public DeploymentPort deploymentPort(
            @ConfigProperty(name = "egen.nomad.address", defaultValue = "http://localhost:4646")
            String nomadAddress,
            @ConfigProperty(name = "egen.nomad.token")
            Optional<String> nomadToken) {
        return new NomadDeploymentAdapter(URI.create(nomadAddress), nomadToken.orElse(null));
    }
}
