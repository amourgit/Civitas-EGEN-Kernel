package africa.civitas.egen.kernel.pluginengine.manifest;

import java.util.Map;

/**
 * Abstrait la provenance des donnees brutes d'un Manifeste d'Extension — le premier
 * point d'extensibilite du moteur de plugins. {@link ManifestReader} ne connait
 * jamais le format physique (fichier `.properties`, ressource embarquee, donnees
 * generees en test...), seulement cette interface.
 *
 * <p>Implementation livree : {@link PropertiesFileManifestSource}, qui lit un fichier
 * `.properties` — le meme format de base que le descripteur natif de PF4J
 * (`plugin.properties`), volontairement reutilise plutot qu'un format concurrent
 * (JSON, YAML) pour eviter toute dependance supplementaire et rester dans l'esprit du
 * mecanisme d'accueil qu'il complete. Rien n'empeche une future implementation
 * (`ClasspathResourceManifestSource`, `DatabaseManifestSource`...) de fournir les
 * memes cles autrement.
 */
public interface ManifestSource {

    /**
     * @return les paires cle-valeur brutes du Manifeste, non encore validees. Les
     *         cles attendues par {@link ManifestReader} sont : {@code moduleId},
     *         {@code version}, et, optionnelles, {@code cellTypesProvided},
     *         {@code mandatesProvided}, {@code eventsEmitted}, {@code eventsConsumed},
     *         {@code resourceTypesProvided}, {@code dependencies} — ces six dernieres
     *         en valeurs separees par des virgules.
     * @throws ManifestReadException si les donnees brutes ne peuvent pas etre lues du
     *                                tout (fichier absent, illisible...) — a distinguer
     *                                d'un Manifeste lisible mais invalide, qui leve
     *                                {@link africa.civitas.egen.kernel.sdk.manifest.ManifestValidationException}
     *                                plus loin, dans {@link ManifestReader}.
     */
    Map<String, String> donneesBrutes();
}
