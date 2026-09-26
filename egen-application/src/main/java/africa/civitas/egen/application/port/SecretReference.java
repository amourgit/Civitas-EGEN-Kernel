package africa.civitas.egen.application.port;

/**
 * Reference a un secret declaree par un service (voir
 * docs/architecture/06-service-manifest.md, "configuration.secretsRefs").
 * Ne contient jamais de valeur — uniquement de quoi la localiser chez le
 * fournisseur (Vault en V1).
 */
public record SecretReference(String key, String provider, String path) {

    public SecretReference {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("SecretReference.key ne peut pas etre vide");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("SecretReference.provider ne peut pas etre vide");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("SecretReference.path ne peut pas etre vide");
        }
    }
}
