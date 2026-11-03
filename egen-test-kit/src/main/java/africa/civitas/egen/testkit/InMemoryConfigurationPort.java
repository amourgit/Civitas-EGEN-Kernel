package africa.civitas.egen.testkit;

import africa.civitas.egen.application.port.ConfigChangeHandler;
import africa.civitas.egen.application.port.ConfigSet;
import africa.civitas.egen.application.port.ConfigVersion;
import africa.civitas.egen.application.port.ConfigWatch;
import africa.civitas.egen.application.port.ConfigurationException;
import africa.civitas.egen.application.port.ConfigurationPort;
import africa.civitas.egen.application.port.ResolvedConfig;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link ConfigurationPort} en memoire : un jeu de valeurs versionne par couple (service, environnement),
 * {@code publish} remplace le jeu entier — comme le blob unique de l'adapter Consul KV.
 * {@link #failing(boolean)} simule un store injoignable. Pas de {@code watch} : aucun test de ce kit n'en a besoin.
 */
public class InMemoryConfigurationPort implements ConfigurationPort {

    private final Map<String, Map<String, String>> stored = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> versions = new ConcurrentHashMap<>();
    private volatile boolean failing;

    public void failing(boolean failing) {
        this.failing = failing;
    }

    public void clear() {
        stored.clear();
        versions.clear();
        failing = false;
    }

    private static String key(ServiceId id, TargetEnvironment environment) {
        return environment.name() + "/" + id.value();
    }

    private void checkAvailable() {
        if (failing) {
            throw new ConfigurationException("Store de configuration injoignable (double de test)");
        }
    }

    @Override
    public ResolvedConfig resolve(ServiceId id, TargetEnvironment environment) {
        checkAvailable();
        String key = key(id, environment);
        return new ResolvedConfig(stored.getOrDefault(key, Map.of()), currentVersion(id, environment));
    }

    @Override
    public void publish(ServiceId id, TargetEnvironment environment, ConfigSet values) {
        checkAvailable();
        String key = key(id, environment);
        stored.put(key, Map.copyOf(values.values()));
        versions.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public ConfigWatch watch(ServiceId id, TargetEnvironment environment, ConfigChangeHandler handler) {
        throw new UnsupportedOperationException("watch n'est pas simule par ce double");
    }

    @Override
    public void unwatch(ConfigWatch watch) {
    }

    @Override
    public ConfigVersion currentVersion(ServiceId id, TargetEnvironment environment) {
        checkAvailable();
        AtomicLong version = versions.get(key(id, environment));
        return new ConfigVersion(version == null ? 0L : version.get());
    }
}
