package africa.civitas.egen.adapter.vault;

import africa.civitas.egen.application.port.SecretRef;
import africa.civitas.egen.application.port.SecretReference;
import africa.civitas.egen.application.port.SecretsException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test de niveau 3 (voir docs/architecture/17-strategie-de-tests.md) : demarre
 * un vrai Vault via Testcontainers et verifie le mapping de
 * {@link VaultSecretsAdapter} contre l'API HTTP reelle — en particulier
 * qu'il ne fait jamais que verifier l'existence, sans jamais lire la valeur
 * (voir docs/architecture/14-securite.md).
 */
@Testcontainers
class VaultSecretsAdapterIT {

    private static final String ROOT_TOKEN = "egen-test-root-token";

    @Container
    static final VaultContainer<?> VAULT = new VaultContainer<>("hashicorp/vault:1.13")
            .withVaultToken(ROOT_TOKEN)
            .withSecretInVault("secret/news-service/db", "password=super-secret-value");

    private VaultSecretsAdapter adapter() {
        URI baseUri = URI.create("http://" + VAULT.getHost() + ":" + VAULT.getMappedPort(8200));
        return new VaultSecretsAdapter(baseUri, ROOT_TOKEN);
    }

    @Test
    void resolveConfirmsExistenceAndReturnsOnlyTheReferenceNeverTheValue() {
        SecretReference reference = new SecretReference("DB_PASSWORD", "vault", "secret/data/news-service/db");

        SecretRef ref = adapter().resolve(reference);

        assertEquals("secret/data/news-service/db", ref.mountPath());
        assertEquals("vault", ref.provider());
        // Le contrat du type lui-meme ne porte aucun champ "value" — la
        // valeur ne transite jamais par le Kernel (voir SecretRef).
    }

    @Test
    void resolveFailsForAnUndeclaredSecretPath() {
        SecretReference reference = new SecretReference("DB_PASSWORD", "vault", "secret/data/never-declared/db");

        assertThrows(SecretsException.class, () -> adapter().resolve(reference));
    }
}
