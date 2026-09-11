package africa.civitas.egen.kernel.bootstrap.config;

import africa.civitas.egen.kernel.pluginengine.loader.Pf4jPluginLoader;
import africa.civitas.egen.kernel.pluginengine.loader.PluginLoader;
import africa.civitas.egen.kernel.pluginprocess.grpc.RpcPluginLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie la logique de {@link KernelBootConfig#pluginLoader()} directement, sans
 * conteneur CDI — meme discipline que {@code PluginLifecycleManagerTest} : la
 * decision (quelle implementation choisir selon {@code egen.kernel.plugin-loader})
 * est independante du mecanisme CDI qui l'expose ensuite au reste de
 * l'application. Un test CDI complet (verifier que le bean est reellement
 * injectable) existe deja pour ce meme producteur via les tests de
 * {@code KernelBootSequenceTest} et {@link KernelBootConfigEventBusTest}, qui
 * exercent l'application assemblee ; ce test-ci se concentre uniquement sur la
 * decision elle-meme, jamais sur le cablage.
 */
class KernelBootConfigPluginLoaderTest {

    private static KernelBootConfig config(String implementation) {
        KernelBootConfig config = new KernelBootConfig();
        config.implementationPluginLoader = implementation;
        config.delaiHandshakeRpc = Duration.ofSeconds(10);
        config.delaiAppelRpc = Duration.ofSeconds(30);
        config.delaiArretPropreRpc = Duration.ofSeconds(5);
        config.objectMapper = new ObjectMapper();
        return config;
    }

    @Test
    void pf4jIsTheDefaultImplementation() {
        PluginLoader loader = config("pf4j").pluginLoader();

        assertTrue(loader instanceof Pf4jPluginLoader);
    }

    @Test
    void rpcCanBeSelectedByConfigurationAlone() {
        PluginLoader loader = config("rpc").pluginLoader();

        assertTrue(loader instanceof RpcPluginLoader);
    }

    @Test
    void anUnknownValueFailsExplicitlyRatherThanSilentlyFallingBackToADefault() {
        assertThrows(IllegalStateException.class, () -> config("inconnu").pluginLoader());
    }
}
