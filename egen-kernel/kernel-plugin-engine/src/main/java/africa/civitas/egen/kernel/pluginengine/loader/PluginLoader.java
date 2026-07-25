package africa.civitas.egen.kernel.pluginengine.loader;

import africa.civitas.egen.kernel.pluginengine.registry.ExtensionDecouverte;

import java.nio.file.Path;
import java.util.List;

/**
 * Abstrait le mecanisme physique de chargement/dechargement d'un plugin — le
 * deuxieme point d'extensibilite du moteur de plugins, apres {@link
 * africa.civitas.egen.kernel.pluginengine.manifest.ManifestSource}.
 *
 * <p>Implementation livree : {@link Pf4jPluginLoader}, adossee a PF4J (le choix
 * technologique acte pour EGEN — JPMS + PF4J en remplacement d'OSGi). Rien
 * n'empeche une future implementation alternative (un chargeur de test en memoire,
 * un mecanisme different si PF4J devait un jour etre remplace) de servir ce meme
 * contrat sans qu'aucun consommateur ({@link
 * africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager}) n'ait a
 * changer — c'est precisement ce qui rend ce module "completement extensible".
 *
 * <p>Ce contrat ne prend jamais de decision d'autorisation lui-meme : il execute
 * mecaniquement ce qu'on lui demande. Charger un plugin non autorise n'est jamais
 * la faute du {@code PluginLoader} — c'est a l'appelant de ne jamais le lui demander
 * pour un candidat refuse.
 */
public interface PluginLoader {

    /**
     * Charge et demarre physiquement le plugin trouve a {@code cheminPlugin}.
     *
     * @return les extensions decouvertes chez ce plugin par reflexion (classes
     *         annotees {@code @Extension}) — jamais nul, une liste vide si le plugin
     *         n'en fournit aucune
     * @throws PluginLoadException si le chargement physique echoue (JAR corrompu,
     *                              classe {@code @Module} absente ou dupliquee,
     *                              erreur de classloading...)
     */
    List<ExtensionDecouverte> charger(String moduleId, Path cheminPlugin);

    /**
     * Arrete et decharge physiquement le plugin {@code moduleId}. Sans effet si ce
     * module n'est pas actuellement charge.
     */
    void decharger(String moduleId);

    boolean estCharge(String moduleId);

    /** @return les identifiants de tous les modules actuellement charges. */
    List<String> modulesCharges();
}
