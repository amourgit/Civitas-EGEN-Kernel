package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.ConfigSet;
import africa.civitas.egen.application.port.ConfigurationException;
import africa.civitas.egen.application.port.ConfigurationPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A3 : valide la publication contre le manifeste (toutes les violations d'un coup), ecrit via le
 * {@code ConfigurationPort}, puis enfile le service pour que la reconciliation — level-triggered, elle relit
 * tout — leve la Condition {@code ConfigResolved}. Le Kernel transporte des paires cle/valeur typees par le
 * manifeste ; il n'en fusionne aucune et ne leur donne aucun sens (docs 22.5).
 *
 * <p>Un service arrete ou en retrait garde sa configuration sans etre enfile : elle s'appliquera a un
 * redeploiement.</p>
 */
public final class PublishConfigurationUseCaseImpl implements PublishConfigurationUseCase {

    private static final Set<Phase> NOT_RECONCILED = Set.of(
            Phase.STOPPING, Phase.STOPPED, Phase.REMOVING, Phase.REMOVED);

    private final RegistryStorePort registryStorePort;
    private final ConfigurationPort configurationPort;
    private final WorkQueue workQueue;

    public PublishConfigurationUseCaseImpl(RegistryStorePort registryStorePort, ConfigurationPort configurationPort,
                                            WorkQueue workQueue) {
        this.registryStorePort = Objects.requireNonNull(registryStorePort, "registryStorePort");
        this.configurationPort = Objects.requireNonNull(configurationPort, "configurationPort");
        this.workQueue = Objects.requireNonNull(workQueue, "workQueue");
    }

    @Override
    public PublishedConfiguration publish(ServiceId id, TargetEnvironment environment, Map<String, String> values) {
        DesiredState desired = registryStorePort.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));

        List<ConfigurationViolation> violations = desired.manifest().configuration().validatePublication(values)
                .stream().map(ConfigurationViolation::from).toList();
        if (!violations.isEmpty()) {
            throw new InvalidConfigurationException(violations);
        }

        long version;
        try {
            configurationPort.publish(id, environment, new ConfigSet(values));
            version = configurationPort.currentVersion(id, environment).index();
        } catch (ConfigurationException e) {
            throw new ConfigurationStoreUnavailableException(
                    "Store de configuration indisponible : rien n'a ete publie pour " + id.value(), e);
        }

        Phase phase = registryStorePort.findStatus(id).map(status -> status.phase()).orElse(Phase.DECLARED);
        if (!NOT_RECONCILED.contains(phase)) {
            workQueue.enqueue(id);
        }
        return new PublishedConfiguration(id, environment, version);
    }
}
