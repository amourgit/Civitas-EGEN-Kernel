package africa.civitas.egen.kernel.pluginengine.loader;

import africa.civitas.egen.kernel.pluginengine.manifest.ManifestSource;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Un module candidat au chargement : ou trouver son Manifeste (lu et valide AVANT
 * tout chargement physique, voir {@link
 * africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager}), et ou
 * trouver le plugin lui-meme une fois l'autorisation acquise.
 *
 * @param cheminPlugin emplacement du plugin physique (JAR ou repertoire eclate,
 *                      selon ce que {@link PluginLoader} sait charger)
 * @param manifestSource la source du Manifeste de ce candidat
 */
public record CandidatModule(Path cheminPlugin, ManifestSource manifestSource) {

    public CandidatModule {
        Objects.requireNonNull(cheminPlugin, "cheminPlugin ne peut pas etre nul.");
        Objects.requireNonNull(manifestSource, "manifestSource ne peut pas etre nulle.");
    }
}
