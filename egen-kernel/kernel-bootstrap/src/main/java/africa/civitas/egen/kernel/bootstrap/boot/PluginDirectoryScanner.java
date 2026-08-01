package africa.civitas.egen.kernel.bootstrap.boot;

import africa.civitas.egen.kernel.pluginengine.loader.CandidatModule;
import africa.civitas.egen.kernel.pluginengine.manifest.PropertiesFileManifestSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Decouvre les modules candidats au chargement dans un repertoire de plugins —
 * convention : chaque module y depose un JAR (`&lt;moduleId&gt;.jar`) accompagne
 * d'un fichier de Manifeste sibling (`&lt;moduleId&gt;.properties`), tous deux
 * directement dans le repertoire, jamais imbriques. Un JAR sans fichier `.properties`
 * du meme nom est ignore silencieusement — ce n'est pas un plugin EGEN valide, PF4J
 * seul ne suffit jamais a le rendre chargeable ici (voir la Charte v3, §1 de
 * l'anatomie du Kernel : le Manifeste est la condition, pas une option).
 *
 * <p>Ne fait aucune verification de gouvernance : voir {@code
 * africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager} pour
 * cela. Ce scanner se contente de trouver des paires de fichiers coherentes.
 */
public final class PluginDirectoryScanner {

    private static final String SUFFIXE_PLUGIN = ".jar";
    private static final String SUFFIXE_MANIFESTE = ".properties";

    /**
     * @return les candidats trouves, tries par nom de fichier pour un ordre
     *         reproductible d'un demarrage a l'autre — jamais une liste vide levant
     *         une exception : un repertoire absent ou vide n'est pas une erreur,
     *         seulement l'absence de tout module a charger.
     */
    public List<CandidatModule> scanner(Path repertoirePlugins) {
        if (repertoirePlugins == null) {
            throw new IllegalArgumentException("repertoirePlugins ne peut pas etre nul.");
        }
        if (!Files.isDirectory(repertoirePlugins)) {
            return List.of();
        }

        List<Path> jars;
        try (Stream<Path> entrees = Files.list(repertoirePlugins)) {
            jars = entrees
                    .filter(p -> p.getFileName().toString().endsWith(SUFFIXE_PLUGIN))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new PluginDirectoryScanException(
                    "Impossible de parcourir le repertoire de plugins " + repertoirePlugins + " : "
                            + e.getMessage(), e);
        }

        List<CandidatModule> candidats = new ArrayList<>();
        for (Path jar : jars) {
            String nomBase = nomSansExtension(jar, SUFFIXE_PLUGIN);
            Path manifeste = repertoirePlugins.resolve(nomBase + SUFFIXE_MANIFESTE);
            if (Files.isRegularFile(manifeste)) {
                candidats.add(new CandidatModule(jar, new PropertiesFileManifestSource(manifeste)));
            }
        }
        return List.copyOf(candidats);
    }

    private static String nomSansExtension(Path fichier, String suffixe) {
        String nom = fichier.getFileName().toString();
        return nom.substring(0, nom.length() - suffixe.length());
    }
}
