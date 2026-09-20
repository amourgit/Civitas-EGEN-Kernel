package africa.civitas.egen.application.registry;

import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.application.port.RegistryStorePort;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation de reference d'un {@link RegistryStorePort}, en memoire.
 * Suffisante tant que le Kernel tourne en une seule instance (voir
 * docs/architecture/19-feuille-de-route.md, Phase 1) — remplacee par
 * egen-adapter-postgres-registry en Phase 2 pour la persistance
 * transactionnelle et le verrouillage optimiste multi-instance (voir
 * docs/architecture/08-registry.md). Ce n'est PAS un adapter au sens de
 * egen-adapters/* : elle ne parle a aucun moteur externe, elle vit dans
 * egen-application comme implementation triviale par defaut d'un port que le
 * domaine definit lui-meme.
 */
public final class InMemoryRegistryStore implements RegistryStorePort {

    private final Map<ServiceId, DesiredState> desiredStates = new ConcurrentHashMap<>();
    private final Map<ServiceId, ServiceStatus> statuses = new ConcurrentHashMap<>();
    private final Map<ServiceId, AtomicLong> generations = new ConcurrentHashMap<>();

    @Override
    public DesiredState save(DesiredState desiredState) {
        ServiceId id = desiredState.serviceId();
        long generation = generations
                .computeIfAbsent(id, ignored -> new AtomicLong(0))
                .incrementAndGet();
        DesiredState withGeneration = new DesiredState(
                desiredState.manifest(), generation, desiredState.targetEnvironment());
        desiredStates.put(id, withGeneration);
        return withGeneration;
    }

    @Override
    public Optional<DesiredState> findById(ServiceId id) {
        return Optional.ofNullable(desiredStates.get(id));
    }

    @Override
    public void saveStatus(ServiceId id, ServiceStatus status) {
        statuses.put(id, status);
    }

    @Override
    public Optional<ServiceStatus> findStatus(ServiceId id) {
        return Optional.ofNullable(statuses.get(id));
    }

    @Override
    public List<ServiceId> findAllIds() {
        return List.copyOf(desiredStates.keySet());
    }
}
