package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Cablage des briques neutres du coeur (voir
 * docs/architecture/16-packages-et-stack-technique.md, "egen-bootstrap").
 * L'implementation en memoire du Registry est celle de Phase 1 (voir
 * docs/architecture/19-feuille-de-route.md) — remplacee par
 * egen-adapter-postgres-registry en Phase 2 en changeant uniquement cette
 * methode, jamais egen-domain ni egen-application (garde-fou n6).
 */
@ApplicationScoped
public class CoreBeans {

    @Produces
    @ApplicationScoped
    public RegistryStorePort registryStorePort() {
        return new InMemoryRegistryStore();
    }

    @Produces
    @ApplicationScoped
    public WorkQueue workQueue() {
        return new WorkQueue();
    }
}
