package africa.civitas.egen.application.observability;

import africa.civitas.egen.application.port.InvocationResult;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.ServiceInvocationPort;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Decore un {@link ServiceInvocationPort} concret des metriques generiques
 * d'appel d'adapter — voir {@link ObservedDeploymentPort} pour le
 * raisonnement complet, identique ici. L'operation mesuree reste "invoke"
 * pour toutes les methodes HTTP (le detail GET/POST/etc. et le chemin
 * appele varient a chaque appel, contrairement aux autres ports decores
 * dont chaque methode Java correspond a une seule operation nommee) — le
 * label {@code operation} resterait sinon a cardinalite non bornee (un
 * chemin HTTP par etape de workflow), ce que les metriques Prometheus
 * doivent eviter.
 */
public final class ObservedServiceInvocationPort implements ServiceInvocationPort {

    private final ServiceInvocationPort delegate;
    private final AdapterCallInstrumentation instrumentation;

    public ObservedServiceInvocationPort(ServiceInvocationPort delegate, ObservabilityPort observabilityPort,
                                          String adapterName) {
        this.delegate = delegate;
        this.instrumentation = new AdapterCallInstrumentation(observabilityPort, adapterName);
    }

    @Override
    public InvocationResult invoke(URI endpoint, String method, Map<String, String> payload, Duration timeout) {
        return instrumentation.call("invoke", () -> delegate.invoke(endpoint, method, payload, timeout));
    }
}
