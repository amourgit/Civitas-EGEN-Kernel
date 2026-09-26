package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.vault.VaultSecretsAdapter;
import africa.civitas.egen.application.port.SecretsPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Optional;

/**
 * Seul point du Kernel qui sait que le SecretsPort est, en V1, implemente
 * par Vault (voir docs/architecture/07-ports-et-adapters.md, "Secrets
 * Port").
 */
@ApplicationScoped
public class SecretsAdapterBeans {

    @Produces
    @ApplicationScoped
    public SecretsPort secretsPort(
            @ConfigProperty(name = "egen.vault.address", defaultValue = "http://localhost:8200")
            String vaultAddress,
            @ConfigProperty(name = "egen.vault.token")
            Optional<String> vaultToken) {
        return new VaultSecretsAdapter(URI.create(vaultAddress), vaultToken.orElse(null));
    }
}
