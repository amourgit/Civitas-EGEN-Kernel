package africa.civitas.egen.kernel.pluginengine.loader;

import africa.civitas.egen.kernel.pluginengine.registry.ExtensionDecouverte;

import java.nio.file.Path;
import java.util.List;

/**
 * Abstrait le mecanisme physique de chargement/dechargement d'un plugin — le
 * deuxieme point d'extensibilite du moteur de plugins, apres {@link
 * africa.civitas.egen.kernel.pluginengine.manifest.ManifestSource}.
 *
 * <p>Deux implementations coexistent : {@link Pf4jPluginLoader}, adossee a PF4J
 * (isolation par classloader, le choix technologique acte pour EGEN en
 * remplacement d'OSGi), et {@code RpcPluginLoader} (isolation par processus
 * separe + mTLS ephemere, module {@code kernel-plugin-process}). La seconde n'a exige aucun changement ici, ni dans {@link
 * africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager} :
 * c'est precisement ce qui rend ce module "completement extensible", pas une
 * promesse abstraite. Le choix entre les deux reste une decision de deploiement.
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
