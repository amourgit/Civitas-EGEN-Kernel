package africa.civitas.egen.application.observability;

import africa.civitas.egen.application.port.ConfigChangeHandler;
import africa.civitas.egen.application.port.ConfigSet;
import africa.civitas.egen.application.port.ConfigVersion;
import africa.civitas.egen.application.port.ConfigWatch;
import africa.civitas.egen.application.port.ConfigurationPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.ResolvedConfig;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;

/**
 * Decore un {@link ConfigurationPort} concret des metriques generiques
 * d'appel d'adapter — voir {@link ObservedDeploymentPort}. Ne journalise
 * ni ne transporte jamais la VALEUR d'une configuration : seules la duree
 * et la reussite/echec de l'appel sont mesurees (les valeurs elles-memes
 * restent de la responsabilite exclusive de l'adapter concret, voir
 * docs/architecture/07-ports-et-adapters.md, "Configuration Port").
 */
public final class ObservedConfigurationPort implements ConfigurationPort {

    private final ConfigurationPort delegate;
    private final AdapterCallInstrumentation instrumentation;

    public ObservedConfigurationPort(ConfigurationPort delegate, ObservabilityPort observabilityPort,
                                      String adapterName) {
        this.delegate = delegate;
        this.instrumentation = new AdapterCallInstrumentation(observabilityPort, adapterName);
    }

    @Override
    public ResolvedConfig resolve(ServiceId id, TargetEnvironment environment) {
        return instrumentation.call("configuration.resolve", () -> delegate.resolve(id, environment));
    }

    @Override
    public void publish(ServiceId id, TargetEnvironment environment, ConfigSet values) {
        instrumentation.run("configuration.publish", () -> delegate.publish(id, environment, values));
    }

    @Override
    public ConfigWatch watch(ServiceId id, TargetEnvironment environment, ConfigChangeHandler handler) {
        return instrumentation.call("configuration.watch", () -> delegate.watch(id, environment, handler));
    }

    @Override
    public void unwatch(ConfigWatch watch) {
        instrumentation.run("configuration.unwatch", () -> delegate.unwatch(watch));
    }

    @Override
    public ConfigVersion currentVersion(ServiceId id, TargetEnvironment environment) {
        return instrumentation.call("configuration.currentVersion", () -> delegate.currentVersion(id, environment));
    }
}
