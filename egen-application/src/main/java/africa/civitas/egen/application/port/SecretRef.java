package africa.civitas.egen.application.port;

/**
 * Ce que {@link SecretsPort#resolve} retourne — une reference a MONTER,
 * jamais la valeur en clair (voir docs/architecture/07-ports-et-adapters.md,
 * "Secrets Port"). {@code mountPath} suit la convention Vault utilisee par
 * l'integration native Nomad-Vault, pour que le Deployment Adapter
 * n'ait jamais a manipuler le secret lui-meme.
 */
public record SecretRef(String mountPath, String provider) {

    public SecretRef {
        if (mountPath == null || mountPath.isBlank()) {
            throw new IllegalArgumentException("SecretRef.mountPath ne peut pas etre vide");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("SecretRef.provider ne peut pas etre vide");
        }
    }
}
