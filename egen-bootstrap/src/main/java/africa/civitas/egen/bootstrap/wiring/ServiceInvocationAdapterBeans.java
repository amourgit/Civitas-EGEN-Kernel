package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.httpinvocation.HttpInvocationAdapter;
import africa.civitas.egen.application.observability.ObservedServiceInvocationPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.ServiceInvocationPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Seul point du Kernel qui sait que le ServiceInvocationPort est implemente
 * par {@link HttpInvocationAdapter} (voir
 * docs/architecture/07-ports-et-adapters.md, "Service Invocation Port").
 * Decore par {@link ObservedServiceInvocationPort} (voir
 * DeploymentAdapterBeans pour le meme raisonnement).
 */
@ApplicationScoped
public class ServiceInvocationAdapterBeans {

    @Inject
    ObservabilityPort observabilityPort;

    @Produces
    @ApplicationScoped
    public ServiceInvocationPort serviceInvocationPort() {
        return new ObservedServiceInvocationPort(new HttpInvocationAdapter(), observabilityPort, "http-invocation");
    }
}
