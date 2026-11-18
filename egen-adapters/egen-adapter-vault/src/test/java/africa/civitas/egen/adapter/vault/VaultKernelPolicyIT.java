package africa.civitas.egen.adapter.vault;

import africa.civitas.egen.application.port.SecretReference;
import africa.civitas.egen.application.port.SecretsException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.vault.VaultContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de niveau 3 (docs/architecture/17) : la politique REELLE du Kernel, le fichier
 * {@code deploy/vault/policy-egen-kernel.hcl} (phase P0, etape P0-4), est ecrite dans un vrai Vault et un jeton qui ne
 * porte qu'elle est utilise. Il prouve que ce jeton suffit au Kernel (verifier l'existence d'un secret) et ne permet
 * RIEN d'autre : ni lire une valeur, ni ecrire, ni administrer Vault.
 */
@Testcontainers
class VaultKernelPolicyIT {

    private static final String ROOT_TOKEN = "egen-test-root-token";
    private static final Path POLICY = Path.of("../../deploy/vault/policy-egen-kernel.hcl");

    @Container
    static final VaultContainer<?> VAULT = new VaultContainer<>("hashicorp/vault:1.13")
            .withVaultToken(ROOT_TOKEN)
            .withSecretInVault("secret/news-service/db", "password=super-secret-value")
            .withCopyFileToContainer(MountableFile.forHostPath(POLICY), "/egen-kernel-policy.hcl");

    private static String kernelToken;

    private static synchronized String kernelToken() throws Exception {
        if (kernelToken == null) {
            String env = "VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=" + ROOT_TOKEN + " ";
            org.testcontainers.containers.Container.ExecResult policy = VAULT.execInContainer("sh", "-c",
                    env + "vault policy write egen-kernel /egen-kernel-policy.hcl");
            assertEquals(0, policy.getExitCode(), policy.getStderr());
            org.testcontainers.containers.Container.ExecResult token = VAULT.execInContainer("sh", "-c",
                    env + "vault token create -policy=egen-kernel -no-default-policy -field=token");
            assertEquals(0, token.getExitCode(), token.getStderr());
            kernelToken = token.getStdout().trim();
        }
        return kernelToken;
    }

    private static URI base() {
        return URI.create("http://" + VAULT.getHost() + ":" + VAULT.getMappedPort(8200));
    }

    private static int status(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(base().resolve("/v1/" + path))
                .header("X-Vault-Token", kernelToken())
                .header("Content-Type", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void theKernelTokenConfirmsTheExistenceOfADeclaredSecret() throws Exception {
        VaultSecretsAdapter adapter = new VaultSecretsAdapter(base(), kernelToken());
        SecretReference reference = new SecretReference("DB_PASSWORD", "vault", "secret/data/news-service/db");

        assertEquals("secret/data/news-service/db", adapter.resolve(reference).mountPath());
    }

    @Test
    void theKernelTokenReportsAnUndeclaredSecretAsMissing() throws Exception {
        VaultSecretsAdapter adapter = new VaultSecretsAdapter(base(), kernelToken());
        SecretReference reference = new SecretReference("DB_PASSWORD", "vault", "secret/data/never-declared/db");

        SecretsException error = assertThrows(SecretsException.class, () -> adapter.resolve(reference));
        assertTrue(error.getMessage().contains("Aucun secret"), error.getMessage());
    }

    @Test
    void theKernelTokenCannotReadTheValueOfASecret() throws Exception {
        assertEquals(403, status("GET", "secret/data/news-service/db", null));
    }

    @Test
    void theKernelTokenCannotWriteASecret() throws Exception {
        assertEquals(403, status("PUT", "secret/data/news-service/written-by-the-kernel", "{\"data\":{\"a\":\"b\"}}"));
    }

    @Test
    void theKernelTokenCannotAdministerVault() throws Exception {
        assertEquals(403, status("GET", "sys/policies/acl/egen-kernel", null));
    }
}
