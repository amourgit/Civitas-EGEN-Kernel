package africa.civitas.egen.kernel.pluginengine.registry;

import africa.civitas.egen.kernel.sdk.extension.Extension;
import africa.civitas.egen.kernel.sdk.extension.ExtensionPoint;

import java.util.Objects;

/**
 * Une implementation concrete d'un {@link ExtensionPoint}, decouverte par un {@link
 * africa.civitas.egen.kernel.pluginengine.loader.PluginLoader} chez un module charge.
 *
 * <p>Verifie explicitement, a la construction, que {@code instance} implemente
 * effectivement {@code point} — la coherence que {@link Extension} promet "verifiee
 * au demarrage, pas seulement esperee par convention". Cette verification est
 * necessaire ici precisement parce que la decouverte reelle se fait par reflexion
 * (chez l'implementation de {@code PluginLoader}), un contexte ou les garanties du
 * compilateur Java ne s'appliquent plus.
 *
 * @param point le point d'extension servi
 * @param instance l'implementation concrete
 * @param priority ordre d'evaluation si plusieurs extensions servent le meme point
 *                 (croissant — voir {@link Extension#priority()})
 * @param moduleId le module qui a fourni cette extension, pour permettre son retrait
 *                 complet au dechargement de ce module
 */
public record ExtensionDecouverte(
        Class<? extends ExtensionPoint> point,
        ExtensionPoint instance,
        int priority,
        String moduleId) {

    public ExtensionDecouverte {
        Objects.requireNonNull(point, "point ne peut pas etre nul.");
        Objects.requireNonNull(instance, "instance ne peut pas etre nulle.");
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId ne peut pas etre vide.");
        }
        if (!point.isInstance(instance)) {
            throw new IllegalArgumentException(
                    "L'instance fournie (" + instance.getClass().getName() + ") du module '" + moduleId
                            + "' n'implemente pas le point d'extension declare (" + point.getName() + ").");
        }
    }
}
