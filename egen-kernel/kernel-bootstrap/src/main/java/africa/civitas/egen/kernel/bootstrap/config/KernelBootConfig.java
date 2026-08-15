package africa.civitas.egen.kernel.bootstrap.config;

import africa.civitas.egen.kernel.bootstrap.boot.KernelBootSequence;
import africa.civitas.egen.kernel.bootstrap.boot.PluginDirectoryScanner;
import africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager;
import africa.civitas.egen.kernel.pluginengine.loader.Pf4jPluginLoader;
import africa.civitas.egen.kernel.pluginengine.loader.PluginLoader;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReader;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Cablage CDI pur — assemble {@link KernelBootSequence} a partir de la configuration
 * et des beans deja disponibles. Aucune logique metier ici, conformement au principe
 * pose pour ce module (voir sa description dans le pom.xml).
 *
 * <p>Trois producteurs sont necessaires ici parce que {@code ManifestReader}, {@code
 * ExtensionRegistry} et {@code PluginLoader} (kernel-plugin-engine) sont des classes
 * volontairement simples, sans annotation CDI propre — instanciables a la main dans
 * les tests, comme documente dans leur propre module. C'est kernel-bootstrap, la
 * racine de composition, qui leur donne une portee CDI, jamais kernel-plugin-engine
 * lui-meme.
 *
 * <p>{@code egen.kernel.contexte-racine} est obligatoire, sans valeur par defaut :
 * une valeur inventee silencieusement serait pire qu'un echec de demarrage franc et
 * explicite — voir le README pour la simplification assumee que represente cet
 * unique Contexte racine au demarrage.
 */
@ApplicationScoped
public class KernelBootConfig {

    @Inject
    PluginLifecycleManager pluginLifecycleManager;

    @ConfigProperty(name = "egen.kernel.plugins-directory", defaultValue = "plugins")
    String repertoirePlugins;

    @ConfigProperty(name = "egen.kernel.contexte-racine")
    UUID contexteRacine;

    @Produces
    @ApplicationScoped
    public ManifestReader manifestReader() {
        return new ManifestReader();
    }

    @Produces
    @ApplicationScoped
    public ExtensionRegistry extensionRegistry() {
        return new ExtensionRegistry();
    }

    @Produces
    @ApplicationScoped
    public PluginLoader pluginLoader() {
        return new Pf4jPluginLoader();
    }

    @Produces
    @ApplicationScoped
    public KernelBootSequence kernelBootSequence() {
        return new KernelBootSequence(
                new PluginDirectoryScanner(), pluginLifecycleManager, Path.of(repertoirePlugins), contexteRacine);
    }
}
