package africa.civitas.egen.application.observability;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentHandle;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceVersion;

import java.util.List;

/**
 * Decore un {@link DeploymentPort} concret des metriques generiques
 * d'appel d'adapter (voir {@link AdapterCallInstrumentation} et
 * docs/architecture/15-observabilite.md). Cable dans egen-bootstrap
 * autour de l'adapter reel (ex. {@code NomadDeploymentAdapter}) — ce
 * decorateur lui-meme ne connait aucun SDK d'adapter concret (garde-fou
 * n2, docs/architecture/02-principes-fondamentaux.md).
 */
public final class ObservedDeploymentPort implements DeploymentPort {

    private final DeploymentPort delegate;
    private final AdapterCallInstrumentation instrumentation;

    public ObservedDeploymentPort(DeploymentPort delegate, ObservabilityPort observabilityPort, String adapterName) {
        this.delegate = delegate;
        this.instrumentation = new AdapterCallInstrumentation(observabilityPort, adapterName);
    }

    @Override
    public DeploymentHandle create(ServiceId id, DeploymentSpec spec) {
        return instrumentation.call("deployment.create", () -> delegate.create(id, spec));
    }

    @Override
    public void update(ServiceId id, DeploymentSpec newSpec) {
        instrumentation.run("deployment.update", () -> delegate.update(id, newSpec));
    }

    @Override
    public void scale(ServiceId id, int replicas) {
        instrumentation.run("deployment.scale", () -> delegate.scale(id, replicas));
    }

    @Override
    public void restart(ServiceId id) {
        instrumentation.run("deployment.restart", () -> delegate.restart(id));
    }

    @Override
    public void stop(ServiceId id) {
        instrumentation.run("deployment.stop", () -> delegate.stop(id));
    }

    @Override
    public void remove(ServiceId id) {
        instrumentation.run("deployment.remove", () -> delegate.remove(id));
    }

    @Override
    public DeploymentObservation getStatus(ServiceId id) {
        return instrumentation.call("deployment.getStatus", () -> delegate.getStatus(id));
    }

    @Override
    public List<AllocationInfo> listAllocations(ServiceId id) {
        return instrumentation.call("deployment.listAllocations", () -> delegate.listAllocations(id));
    }

    @Override
    public void rollback(ServiceId id, ServiceVersion targetVersion) {
        instrumentation.run("deployment.rollback", () -> delegate.rollback(id, targetVersion));
    }
}
