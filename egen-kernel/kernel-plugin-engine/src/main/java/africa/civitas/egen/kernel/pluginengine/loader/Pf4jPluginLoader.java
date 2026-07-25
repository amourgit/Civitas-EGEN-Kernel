package africa.civitas.egen.kernel.pluginengine.loader;

import africa.civitas.egen.kernel.pluginengine.registry.ExtensionDecouverte;
import africa.civitas.egen.kernel.sdk.extension.Extension;
import africa.civitas.egen.kernel.sdk.extension.ExtensionPoint;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Implementation livree de {@link PluginLoader}, adossee a PF4J pour tout ce qu'il
 * fait bien — l'isolation de classloader par plugin, le cycle de vie physique
 * (chargement, demarrage, arret, dechargement). La decouverte des extensions
 * EGEN elle-meme reste entierement du ressort de ce module : PF4J recherche sa
 * PROPRE annotation ({@code org.pf4j.Extension}) par defaut, jamais celle d'EGEN
 * ({@link Extension}) — voir le javadoc de {@link Extension}, qui est explicite sur
 * ce point ("le moteur de plugins... recherche les classes annotees Extension").
 * Cette classe fait donc elle-meme, apres que PF4J a charge et isole le plugin, le
 * balayage des classes du JAR a la recherche de l'annotation EGEN, plutot que de
 * detourner le mecanisme d'extension natif de PF4J vers une annotation qui n'est pas
 * la sienne.
 *
 * <p><b>Portee des tests</b> : ce module n'a, a ce jour, aucun plugin PF4J reel a
 * charger dans ce depot (aucun module business n'est encore empaquete comme tel).
 * Cette classe n'est donc pas couverte par un test d'integration reel ici — toute la
 * logique de decision (l'essentiel de la valeur du moteur de plugins) l'est,
 * entierement, via {@link
 * africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager} et un
 * {@link PluginLoader} de test. Cette classe merite une attention particuliere en
 * revue avant sa premiere utilisation reelle.
 */
public final class Pf4jPluginLoader implements PluginLoader {

    private final PluginManager pluginManager;

    public Pf4jPluginLoader() {
        this(new DefaultPluginManager());
    }

    /** Constructeur visible pour les tests — permet d'injecter un {@code PluginManager} de test. */
    Pf4jPluginLoader(PluginManager pluginManager) {
        if (pluginManager == null) {
            throw new IllegalArgumentException("pluginManager ne peut pas etre nul.");
        }
        this.pluginManager = pluginManager;
    }

    @Override
    public List<ExtensionDecouverte> charger(String moduleId, Path cheminPlugin) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId ne peut pas etre vide.");
        }
        if (cheminPlugin == null) {
            throw new IllegalArgumentException("cheminPlugin ne peut pas etre nul.");
        }

        String idCharge;
        try {
            idCharge = pluginManager.loadPlugin(cheminPlugin);
        } catch (RuntimeException e) {
            throw new PluginLoadException(
                    "Echec du chargement physique du plugin '" + moduleId + "' depuis " + cheminPlugin
                            + " : " + e.getMessage(), e);
        }
        if (idCharge == null) {
            throw new PluginLoadException(
                    "PF4J n'a pas pu charger le plugin '" + moduleId + "' depuis " + cheminPlugin + ".");
        }
        if (!idCharge.equals(moduleId)) {
            pluginManager.unloadPlugin(idCharge);
            throw new PluginLoadException(
                    "L'identifiant declare par le plugin charge ('" + idCharge + "') ne correspond pas "
                            + "au moduleId attendu ('" + moduleId + "') : chargement annule.");
        }

        PluginState etat = pluginManager.startPlugin(moduleId);
        if (etat != PluginState.STARTED) {
            pluginManager.unloadPlugin(moduleId);
            throw new PluginLoadException(
                    "Le plugin '" + moduleId + "' n'a pas pu demarrer (etat obtenu : " + etat + ").");
        }

        try {
            return decouvrirExtensions(moduleId, cheminPlugin);
        } catch (RuntimeException e) {
            pluginManager.unloadPlugin(moduleId);
            throw new PluginLoadException(
                    "Echec de la decouverte des extensions du plugin '" + moduleId + "' : "
                            + e.getMessage(), e);
        }
    }

    @Override
    public void decharger(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId ne peut pas etre vide.");
        }
        if (estCharge(moduleId)) {
            pluginManager.unloadPlugin(moduleId);
        }
    }

    @Override
    public boolean estCharge(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId ne peut pas etre vide.");
        }
        PluginWrapper plugin = pluginManager.getPlugin(moduleId);
        return plugin != null && plugin.getPluginState() == PluginState.STARTED;
    }

    @Override
    public List<String> modulesCharges() {
        return pluginManager.getStartedPlugins().stream()
                .map(PluginWrapper::getPluginId)
                .toList();
    }

    /**
     * Balaie le JAR du plugin a la recherche de classes annotees {@link Extension},
     * les instancie via le classloader isole que PF4J a attribue a ce plugin, et les
     * verifie contre le point d'extension qu'elles declarent servir.
     */
    private List<ExtensionDecouverte> decouvrirExtensions(String moduleId, Path cheminPlugin) {
        PluginWrapper plugin = pluginManager.getPlugin(moduleId);
        if (plugin == null) {
            throw new PluginLoadException(
                    "Plugin '" + moduleId + "' introuvable aupres de PF4J apres son propre demarrage.");
        }
        ClassLoader classLoaderIsole = plugin.getPluginClassLoader();

        List<ExtensionDecouverte> decouvertes = new ArrayList<>();
        try (JarFile jar = new JarFile(cheminPlugin.toFile())) {
            Enumeration<JarEntry> entrees = jar.entries();
            while (entrees.hasMoreElements()) {
                JarEntry entree = entrees.nextElement();
                if (entree.isDirectory() || !entree.getName().endsWith(".class")) {
                    continue;
                }
                String nomClasse = entree.getName()
                        .substring(0, entree.getName().length() - ".class".length())
                        .replace('/', '.');

                Class<?> classe;
                try {
                    classe = Class.forName(nomClasse, false, classLoaderIsole);
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    continue;
                }

                Extension annotation = classe.getAnnotation(Extension.class);
                if (annotation == null) {
                    continue;
                }

                decouvertes.add(instancier(classe, annotation, moduleId));
            }
        } catch (IOException e) {
            throw new PluginLoadException(
                    "Impossible de parcourir le JAR du plugin '" + moduleId + "' : " + e.getMessage(), e);
        }

        return List.copyOf(decouvertes);
    }

    @SuppressWarnings("unchecked")
    private static ExtensionDecouverte instancier(Class<?> classe, Extension annotation, String moduleId) {
        Class<? extends ExtensionPoint> point = annotation.point();
        Object instance;
        try {
            instance = classe.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new PluginLoadException(
                    "Impossible d'instancier l'extension " + classe.getName() + " du module '"
                            + moduleId + "' (constructeur sans argument requis) : " + e.getMessage(), e);
        }
        if (!(instance instanceof ExtensionPoint extensionPoint)) {
            throw new PluginLoadException(
                    "La classe " + classe.getName() + " du module '" + moduleId
                            + "' est annotee @Extension mais n'implemente pas ExtensionPoint.");
        }
        return new ExtensionDecouverte(point, extensionPoint, annotation.priority(), moduleId);
    }

    /** Utilitaire de diagnostic — jamais utilise par la logique de chargement elle-meme. */
    static Path cheminDuJarContenant(Class<?> classe) {
        try {
            URL emplacement = classe.getProtectionDomain().getCodeSource().getLocation();
            return Path.of(emplacement.toURI());
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalStateException("Impossible de determiner l'emplacement du JAR pour "
                    + classe.getName(), e);
        }
    }
}
