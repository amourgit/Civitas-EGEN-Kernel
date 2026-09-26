package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.otel.OtelObservabilityAdapter;
import africa.civitas.egen.application.port.ObservabilityPort;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Seul point du Kernel qui sait que le ObservabilityPort est, en V1,
 * implemente par OpenTelemetry avec export OTLP (voir
 * docs/architecture/07-ports-et-adapters.md, "Observability Port" et
 * docs/architecture/16-packages-et-stack-technique.md).
 */
@ApplicationScoped
public class ObservabilityAdapterBeans {

    private OtelObservabilityAdapter adapter;

    @Produces
    @ApplicationScoped
    public ObservabilityPort observabilityPort(
            @ConfigProperty(name = "egen.otel.endpoint", defaultValue = "http://localhost:4317")
            String otlpEndpoint,
            @ConfigProperty(name = "quarkus.application.name", defaultValue = "egen-kernel")
            String serviceName) {
        if (adapter == null) {
            adapter = new OtelObservabilityAdapter(otlpEndpoint, serviceName);
        }
        return adapter;
    }

    void onStop(@Observes ShutdownEvent event) {
        if (adapter != null) {
            adapter.close();
        }
    }
}
