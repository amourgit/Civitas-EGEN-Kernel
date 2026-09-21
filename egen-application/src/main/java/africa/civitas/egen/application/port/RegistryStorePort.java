package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.List;
import java.util.Optional;

/**
 * Port secondaire — le Registry EGEN (voir docs/architecture/08-registry.md).
 * Version minimale pour cette etape du Kernel : persiste le {@link DesiredState}
 * et le {@link ServiceStatus} courants. L'historique des generations
 * successives et l'instantane du graphe de dependances rejoignent cette
 * interface en Phase 2/3 (voir docs/architecture/19-feuille-de-route.md),
 * jamais par anticipation.
 *
 * <p>Implementation par defaut fournie ici meme : {@link
 * africa.civitas.egen.application.registry.InMemoryRegistryStore}, remplacee
 * par un adapter PostgreSQL en Phase 2 sans qu'egen-application ni
 * egen-domain n'aient a changer (garde-fou n6,
 * docs/architecture/02-principes-fondamentaux.md).</p>
 */
public interface RegistryStorePort {

    /**
     * Persiste un etat desire. L'implementation attribue/incremente
     * {@code generation} — l'appelant transmet la generation qu'il connait
     * (0 pour une premiere declaration), le retour fait foi.
     */
    DesiredState save(DesiredState desiredState);

    Optional<DesiredState> findById(ServiceId id);

    void saveStatus(ServiceId id, ServiceStatus status);

    Optional<ServiceStatus> findStatus(ServiceId id);

    /** Tous les identifiants connus — alimente le resync periodique (voir 04.2). */
    List<ServiceId> findAllIds();

    /** Audit trail des generations successives (voir docs/architecture/08-registry.md). */
    List<DesiredState> history(ServiceId id);
}
