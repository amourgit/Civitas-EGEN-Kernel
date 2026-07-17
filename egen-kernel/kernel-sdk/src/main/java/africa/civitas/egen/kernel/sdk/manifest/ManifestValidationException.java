package africa.civitas.egen.kernel.sdk.manifest;

/**
 * Levee lorsqu'un {@link ManifesteExtension} declare par un module ne respecte pas les
 * regles de coherence minimales du Kernel (identifiant mal forme, version non
 * semantique, auto-dependance, doublons...).
 *
 * <p>Cette validation a lieu a la construction de l'objet, jamais plus tard au moment
 * de l'activation — un Manifeste mal forme ne doit jamais pouvoir exister en memoire.
 */
public class ManifestValidationException extends RuntimeException {

    public ManifestValidationException(String message) {
        super(message);
    }
}
