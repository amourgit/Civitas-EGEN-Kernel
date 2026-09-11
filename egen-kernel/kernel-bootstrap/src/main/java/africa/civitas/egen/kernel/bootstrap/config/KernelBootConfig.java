package africa.civitas.egen.kernel.bootstrap.config;

import africa.civitas.egen.kernel.bootstrap.boot.KernelBootSequence;
import africa.civitas.egen.kernel.bootstrap.boot.PluginDirectoryScanner;
import africa.civitas.egen.kernel.eventbus.api.EventBus;
import africa.civitas.egen.kernel.eventbus.api.InMemoryEventBus;
import africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager;
import africa.civitas.egen.kernel.pluginengine.loader.Pf4jPluginLoader;
import africa.civitas.egen.kernel.pluginengine.loader.PluginLoader;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReader;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionRegistry;
import africa.civitas.egen.kernel.pluginprocess.grpc.RpcPluginLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * Cablage CDI pur — assemble {@link KernelBootSequence} a partir de la configuration
 * et des beans deja disponibles. Aucune logique metier ici, conformement au principe
 * pose pour ce module (voir sa description dans le pom.xml).
 *
 * <p>Quatre producteurs sont necessaires ici parce que {@code ManifestReader}, {@code
 * ExtensionRegistry}, {@code PluginLoader} (kernel-plugin-engine) et {@code
 * InMemoryEventBus} (kernel-eventbus) sont des classes volontairement simples, sans
 * annotation CDI propre — instanciables a la main dans les tests, comme documente
 * dans leur propre module. C'est kernel-bootstrap, la racine de composition, qui
 * leur donne une portee CDI, jamais leur module d'origine lui-meme.
 *
 * <p>{@code egen.kernel.contexte-racine} est obligatoire, sans valeur par defaut :
 * une valeur inventee silencieusement serait pire qu'un echec de demarrage franc et
 * explicite — voir le README pour la simplification assumee que represente cet
 * unique Contexte racine au demarrage.
 *
 * <p>{@code egen.kernel.plugin-loader} (depuis le 11 septembre 2026) est le premier
 * exemple, dans ce Kernel, d'une capacite choisie par configuration plutot que par
 * edition de ce fichier : voir {@link #pluginLoader()}. Le meme patron (une
 * propriete de configuration lue dans un seul producteur, jamais une ambiguite de
 * resolution CDI) est le candidat naturel pour toute future capacite a plusieurs
 * implementations concretes — Identite comprise, le jour ou un second provider
 * existera reellement.
 */
@ApplicationScoped
public class KernelBootConfig {

    @Inject
    PluginLifecycleManager pluginLifecycleManager;

    @ConfigProperty(name = "egen.kernel.plugins-directory", defaultValue = "plugins")
    String repertoirePlugins;

    @ConfigProperty(name = "egen.kernel.contexte-racine")
    UUID contexteRacine;

    @ConfigProperty(name = "egen.kernel.plugin-loader", defaultValue = "pf4j")
    String implementationPluginLoader;

    @ConfigProperty(name = "egen.kernel.plugin-loader.rpc.delai-handshake", defaultValue = "PT10S")
    Duration delaiHandshakeRpc;

    @ConfigProperty(name = "egen.kernel.plugin-loader.rpc.delai-appel", defaultValue = "PT30S")
    Duration delaiAppelRpc;

    @ConfigProperty(name = "egen.kernel.plugin-loader.rpc.delai-arret-propre", defaultValue = "PT5S")
    Duration delaiArretPropreRpc;

    @Inject
    ObjectMapper objectMapper;

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

    /**
     * Premier registre de capacite declaratif du Kernel (Capability -> Provider) :
     * {@code egen.kernel.plugin-loader} choisit, par configuration et non par
     * edition de ce fichier, laquelle des deux implementations reelles de {@link
     * PluginLoader} est active — {@code pf4j} (isolation par classloader,
     * {@link Pf4jPluginLoader}, la voie par defaut, legere, aucun processus
     * additionnel a superviser) ou {@code rpc} (isolation par processus separe +
     * mTLS ephemere, {@code RpcPluginLoader}, kernel-plugin-process, pour les
     * modules qui en ont reellement besoin).
     *
     * <p>Un seul producteur, jamais deux beans candidats pour le meme type : aucune
     * ambiguite de resolution CDI possible, quelle que soit la valeur configuree.
     * Une valeur ni {@code pf4j} ni {@code rpc} echoue explicitement, au premier
     * usage reel de ce producteur — jamais un remplacement silencieux par un choix
     * par defaut non demande.
     */
    @Produces
    @ApplicationScoped
    public PluginLoader pluginLoader() {
        return switch (implementationPluginLoader) {
            case "pf4j" -> new Pf4jPluginLoader();
            case "rpc" -> new RpcPluginLoader(delaiHandshakeRpc, delaiAppelRpc, delaiArretPropreRpc, objectMapper);
            default -> throw new IllegalStateException(
                    "egen.kernel.plugin-loader invalide : '" + implementationPluginLoader
                            + "' (valeurs acceptees : 'pf4j', 'rpc').");
        };
    }

    /**
     * {@link InMemoryEventBus} (Niveau 0, sans dependance externe) reste le repli
     * par defaut — aucun module charge dynamiquement aujourd'hui n'a encore besoin
     * de franchir une frontiere de processus pour publier ou souscrire. {@code
     * KafkaEventBusAdapter} (kernel-eventbus/eventbus-kafka-adapter, deja une
     * dependance de ce module) reste l'alternative pour un deploiement qui en a
     * reellement besoin : l'activer exige de changer ce seul producteur, jamais
     * ailleurs dans le Kernel — meme discipline que {@link #pluginLoader()}
     * ci-dessus.
     */
    @Produces
    @ApplicationScoped
    public EventBus eventBus() {
        return new InMemoryEventBus();
    }

    @Produces
    @ApplicationScoped
    public KernelBootSequence kernelBootSequence() {
        return new KernelBootSequence(
                new PluginDirectoryScanner(), pluginLifecycleManager, Path.of(repertoirePlugins), contexteRacine);
    }
}
