package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.application.reconciliation.PendingDeclareTraces;
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

    /**
     * UNE seule instance partagee entre les use cases (qui y enregistrent
     * le traceparent d'un Declare/Stop) et le ReconciliationEngine (qui le
     * relit) — voir docs/architecture/15-observabilite.md et
     * PendingDeclareTraces. Un producteur separe par module cablerait deux
     * instances isolees et romprait silencieusement le lien de trace.
     */
    @Produces
    @ApplicationScoped
    public PendingDeclareTraces pendingDeclareTraces() {
        return new PendingDeclareTraces();
    }
}
