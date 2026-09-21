package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.application.reconciliation.WorkQueue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Cablage des briques neutres du coeur (voir
 * docs/architecture/16-packages-et-stack-technique.md, "egen-bootstrap").
 * Le Registry (RegistryStorePort) est cable separement dans
 * {@link RegistryAdapterBeans} — voir ce fichier pour le detail de
 * l'implementation retenue par phase.
 */
@ApplicationScoped
public class CoreBeans {

    @Produces
    @ApplicationScoped
    public WorkQueue workQueue() {
        return new WorkQueue();
    }
}
