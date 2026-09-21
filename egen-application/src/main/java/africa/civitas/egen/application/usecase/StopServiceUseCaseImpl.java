package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.reconciliation.WorkQueue;
import africa.civitas.egen.domain.lifecycle.LifecycleStateMachine;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.ServiceId;

public final class StopServiceUseCaseImpl implements StopServiceUseCase {

    private final RegistryStorePort registryStorePort;
    private final WorkQueue workQueue;
    private final LifecycleStateMachine stateMachine = new LifecycleStateMachine();

    public StopServiceUseCaseImpl(RegistryStorePort registryStorePort, WorkQueue workQueue) {
        this.registryStorePort = registryStorePort;
        this.workQueue = workQueue;
    }

    @Override
    public void stop(ServiceId id) {
        if (registryStorePort.findById(id).isEmpty()) {
            throw new ServiceNotFoundException(id);
        }
        ServiceStatus current = registryStorePort.findStatus(id).orElse(ServiceStatus.initial());
        if (current.phase() == Phase.STOPPING || current.phase() == Phase.STOPPED) {
            return; // idempotent : deja en cours d'arret ou arrete
        }
        stateMachine.assertTransitionAllowed(current.phase(), Phase.STOPPING);
        registryStorePort.saveStatus(id,
                new ServiceStatus(Phase.STOPPING, current.conditions(), current.observedGeneration()));
        workQueue.enqueue(id);
    }
}
