package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.vault.VaultSecretsAdapter;
import africa.civitas.egen.application.observability.ObservedSecretsPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.SecretsPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Optional;

/**
 * Seul point du Kernel qui sait que le SecretsPort est, en V1, implemente
 * par Vault (voir docs/architecture/07-ports-et-adapters.md, "Secrets
 * Port"). Decore par {@link ObservedSecretsPort} (voir
 * DeploymentAdapterBeans) — seules la duree et la reussite/echec de
 * chaque appel Vault sont mesurees, jamais la valeur resolue.
 */
@ApplicationScoped
public class SecretsAdapterBeans {

    @Inject
    ObservabilityPort observabilityPort;

    @Produces
    @ApplicationScoped
    public SecretsPort secretsPort(
            @ConfigProperty(name = "egen.vault.address", defaultValue = "http://localhost:8200")
            String vaultAddress,
            @ConfigProperty(name = "egen.vault.token")
            Optional<String> vaultToken) {
        VaultSecretsAdapter adapter = new VaultSecretsAdapter(URI.create(vaultAddress), vaultToken.orElse(null));
        return new ObservedSecretsPort(adapter, observabilityPort, "vault");
    }
}
