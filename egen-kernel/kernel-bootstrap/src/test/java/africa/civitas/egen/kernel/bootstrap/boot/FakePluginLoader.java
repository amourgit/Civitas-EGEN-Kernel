package africa.civitas.egen.kernel.bootstrap.boot;

import africa.civitas.egen.kernel.pluginengine.loader.PluginLoader;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionDecouverte;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Doublure de test de {@code PluginLoader}, entierement en memoire — meme role que
 * son homonyme dans kernel-plugin-engine (petite duplication assumee plutot qu'une
 * dependance de test inter-module, pour rester simple et sans risque). Utilisee ici
 * pour verifier {@link KernelBootSequence} contre de VRAIS
 * KernelPermissionCheck/ModuleActivationResolver/PolitiqueNoyau (via Testcontainers,
 * injectes dans le test), sans avoir besoin d'un plugin JAR physique.
 */
final class FakePluginLoader implements PluginLoader {

    private final Map<String, List<ExtensionDecouverte>> charges = new HashMap<>();

    @Override
    public List<ExtensionDecouverte> charger(String moduleId, Path cheminPlugin) {
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
}
