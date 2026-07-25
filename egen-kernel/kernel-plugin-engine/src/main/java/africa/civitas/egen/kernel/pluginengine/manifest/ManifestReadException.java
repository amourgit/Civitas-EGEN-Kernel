package africa.civitas.egen.kernel.pluginengine.manifest;

/**
 * Levee quand un {@link ManifestSource} ne parvient pas a produire de donnees brutes
 * du tout — fichier absent, illisible, encodage invalide. A distinguer de {@link
 * africa.civitas.egen.kernel.sdk.manifest.ManifestValidationException}, qui concerne
 * des donnees lisibles mais structurellement invalides (identifiant mal forme,
 * version non semantique...). Les deux aboutissent au meme refus fail-closed via
 * {@link africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyauQuestion#ECHEC_CONSTRUCTION_MANIFESTE},
 * mais restent des causes distinctes, utiles a distinguer dans un journal.
 */
public class ManifestReadException extends RuntimeException {

    public ManifestReadException(String message) {
        super(message);
    }

    public ManifestReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
