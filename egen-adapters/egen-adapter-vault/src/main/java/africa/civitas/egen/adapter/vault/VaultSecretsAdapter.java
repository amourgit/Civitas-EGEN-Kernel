package africa.civitas.egen.adapter.vault;

import africa.civitas.egen.application.port.SecretRef;
import africa.civitas.egen.application.port.SecretReference;
import africa.civitas.egen.application.port.SecretsException;
import africa.civitas.egen.application.port.SecretsPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Implementation du {@link SecretsPort} contre l'API HTTP Vault reelle —
 * voir docs/architecture/07-ports-et-adapters.md, "Secrets Port". Utilise
 * l'API KV version 2 de Vault (chemin {@code <mount>/metadata/<path>} pour
 * verifier l'existence — jamais {@code <mount>/data/<path>}, qui retourne la
 * valeur en clair : ce module ne l'appelle jamais, conformement au garde-fou
 * "EGEN ne stocke jamais le secret lui-meme", voir
 * docs/architecture/14-securite.md).
 *
 * <p><b>Limite V1 assumee</b> : {@link #resolve} confirme l'existence de la
 * reference et retourne de quoi la monter (le chemin lui-meme, au format
 * attendu par l'integration native Nomad-Vault — voir 07.5) ; il ne va
 * jamais chercher la valeur. {@link #rotate} revalide simplement que la
 * reference existe toujours : une rotation complete depend du moteur de
 * secret concerne (KV n'a pas de rotation native, contrairement aux
 * secrets dynamiques base de donnees) et reste a affiner avec un besoin
 * reel plutot que par anticipation.</p>
 */
public final class VaultSecretsAdapter implements SecretsPort {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final URI baseUri;
    private final String vaultToken;
    private final HttpClient httpClient;

    public VaultSecretsAdapter(URI baseUri, String vaultToken) {
        this.baseUri = baseUri;
        this.vaultToken = vaultToken;
        this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    }

    @Override
    public SecretRef resolve(SecretReference reference) {
        assertExists(reference);
        return new SecretRef(reference.path(), reference.provider());
    }

    @Override
    public void rotate(SecretReference reference) {
        assertExists(reference);
    }

    private void assertExists(SecretReference reference) {
        String metadataPath = toMetadataPath(reference.path());
        HttpResponse<String> response = send(getRequest(metadataPath));
        if (response.statusCode() == 404) {
            throw new SecretsException("Aucun secret Vault a l'emplacement \"" + reference.path() + "\"");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SecretsException("Vault a rejete la verification de \"" + reference.path()
                    + "\" : HTTP " + response.statusCode() + " — " + response.body());
        }
    }

    /**
     * Convertit un chemin KV v2 style manifeste (ex.
     * "secret/data/news-service/db") vers son equivalent metadata (ex.
     * "secret/metadata/news-service/db") — voir docs/architecture/06-service-manifest.md,
     * "configuration.secretsRefs[].path" pour la convention "<mount>/data/<chemin>".
     */
    private static String toMetadataPath(String dataPath) {
        int dataSegment = dataPath.indexOf("/data/");
        if (dataSegment < 0) {
            throw new SecretsException(
                    "Chemin de secret Vault invalide (attendu \"<mount>/data/<chemin>\") : \"" + dataPath + "\"");
        }
        return dataPath.substring(0, dataSegment) + "/metadata/" + dataPath.substring(dataSegment + 6);
    }

    private HttpRequest getRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve("/v1/" + path))
                .timeout(REQUEST_TIMEOUT)
                .GET();
        if (vaultToken != null && !vaultToken.isBlank()) {
            builder.header("X-Vault-Token", vaultToken);
        }
        return builder.build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SecretsException("Appel Vault impossible (" + request.uri() + ") : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecretsException("Appel Vault interrompu (" + request.uri() + ")", e);
        }
    }
}
