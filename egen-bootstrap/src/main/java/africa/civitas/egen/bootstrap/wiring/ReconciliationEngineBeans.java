package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.reconciliation.ReconciliationEngine;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Cablage et cycle de vie du moteur de reconciliation (voir
 * docs/architecture/04-moteur-de-reconciliation.md). Demarre au boot du
 * Kernel, s'arrete proprement a l'extinction — c'est la boucle de controle
 * permanente qui fait d'EGEN un control plane et non un script d'installation.
 */
@ApplicationScoped
public class ReconciliationEngineBeans {

    @Inject
    WorkQueue workQueue;

    @Inject
    RegistryStorePort registryStorePort;

    @Inject
    DeploymentPort deploymentPort;

    private ReconciliationEngine engine;

    @Produces
    @ApplicationScoped
    public ReconciliationEngine reconciliationEngine() {
        if (engine == null) {
            engine = new ReconciliationEngine(workQueue, registryStorePort, deploymentPort);
        }
        return engine;
    }

    void onStart(@Observes StartupEvent event, ReconciliationEngine reconciliationEngine) {
        reconciliationEngine.start();
    }

    void onStop(@Observes ShutdownEvent event, ReconciliationEngine reconciliationEngine) {
        reconciliationEngine.close();
    }
}
