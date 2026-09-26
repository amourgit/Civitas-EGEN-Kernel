package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;

/**
 * Port secondaire — transport generique de paires cle/valeur, jamais leur
 * signification metier (voir docs/architecture/07-ports-et-adapters.md,
 * "Configuration Port"). Implementation V1 : Consul KV, deja present dans
 * la stack plutot que d'introduire un systeme supplementaire (garde-fou n3,
 * docs/architecture/02-principes-fondamentaux.md).
 */
public interface ConfigurationPort {

    ResolvedConfig resolve(ServiceId id, TargetEnvironment environment);

    void publish(ServiceId id, TargetEnvironment environment, ConfigSet values);

    /** Rechargement a chaud (optionnel cote service) — l'interface l'anticipe des la V1. */
    ConfigWatch watch(ServiceId id, TargetEnvironment environment, ConfigChangeHandler handler);

    void unwatch(ConfigWatch watch);

    ConfigVersion currentVersion(ServiceId id, TargetEnvironment environment);
}
