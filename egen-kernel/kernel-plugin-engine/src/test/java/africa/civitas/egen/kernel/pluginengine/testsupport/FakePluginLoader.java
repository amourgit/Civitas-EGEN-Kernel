package africa.civitas.egen.kernel.pluginengine.testsupport;

import africa.civitas.egen.kernel.pluginengine.loader.PluginLoadException;
import africa.civitas.egen.kernel.pluginengine.loader.PluginLoader;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionDecouverte;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Doublure de test de {@link PluginLoader}, entierement en memoire — la preuve, par
 * l'usage, que ce module est reellement extensible : {@link
 * africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager} ne se
 * comporte pas differemment selon qu'il recoit {@code Pf4jPluginLoader} ou cette
 * doublure, puisqu'il ne connait jamais rien de plus que l'interface {@link
 * PluginLoader}.
 */
public final class FakePluginLoader implements PluginLoader {

    private final Map<String, List<ExtensionDecouverte>> charges = new HashMap<>();
    private final Set<String> echecsSimules = new HashSet<>();

    public FakePluginLoader avecExtensionsPour(String moduleId, List<ExtensionDecouverte> extensions) {
        charges.put(moduleId, extensions);
        return this;
    }

    /** Simule un echec technique de chargement (JAR corrompu...) pour ce moduleId. */
    public FakePluginLoader simulerEchecPour(String moduleId) {
        echecsSimules.add(moduleId);
        return this;
    }

    @Override
    public List<ExtensionDecouverte> charger(String moduleId, Path cheminPlugin) {
        if (echecsSimules.contains(moduleId)) {
            throw new PluginLoadException("Echec simule de chargement pour '" + moduleId + "'.");
        }
        List<ExtensionDecouverte> extensions = charges.getOrDefault(moduleId, List.of());
        charges.put(moduleId, extensions);
        return extensions;
    }

    @Override
    public void decharger(String moduleId) {
        charges.remove(moduleId);
    }

    @Override
    public boolean estCharge(String moduleId) {
        return charges.containsKey(moduleId);
    }

    @Override
    public List<String> modulesCharges() {
        return List.copyOf(charges.keySet());
    }

    /**
     * Marque {@code moduleId} comme deja charge avant meme un appel a {@link
     * #charger} — utile pour simuler l'etat "dependance deja chargee" dans les tests
     * du moteur de plugins sans passer par un chargement complet.
     */
    public FakePluginLoader marquerCharge(String moduleId) {
        charges.putIfAbsent(moduleId, List.of());
        return this;
    }
}
