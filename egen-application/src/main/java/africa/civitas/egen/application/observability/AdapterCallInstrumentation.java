package africa.civitas.egen.application.observability;

import africa.civitas.egen.application.port.MetricSample;
import africa.civitas.egen.application.port.ObservabilityPort;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Instrumentation generique d'un appel a un port secondaire concret
 * (Nomad, Consul, NATS, Postgres, Vault...) — voir
 * docs/architecture/15-observabilite.md, jeu minimal de metriques V1 :
 * {@code egen_adapter_call_duration_seconds{adapter, operation}} et
 * {@code egen_adapter_call_errors_total{adapter, operation}}.
 *
 * <p>Ne cree jamais de span (le tracage applicatif reste au choix explicite
 * de l'appelant — voir
 * {@link africa.civitas.egen.application.reconciliation.ReconciliationEngine},
 * qui ouvre ses propres spans enfants nommes autour des appels de port
 * qu'il orchestre) : cette classe ne fait QUE la metrique de duree/erreur,
 * reutilisable par tout decorateur {@code Observed*Port} sans dependre
 * d'un contexte de trace a threader.</p>
 *
 * <p>Reste dans {@code egen-application} : ne depend que du JDK et des
 * ports d'egen-application (garde-fou n2,
 * docs/architecture/02-principes-fondamentaux.md) — aucun decorateur
 * {@code Observed*Port} ne connait jamais le SDK d'un adapter concret.</p>
 */
public final class AdapterCallInstrumentation {

    private final ObservabilityPort observabilityPort;
    private final String adapterName;

    public AdapterCallInstrumentation(ObservabilityPort observabilityPort, String adapterName) {
        this.observabilityPort = observabilityPort;
        this.adapterName = adapterName;
    }

    public <T> T call(String operation, Supplier<T> action) {
        Instant startedAt = Instant.now();
        try {
            T result = action.get();
            recordDuration(operation, startedAt);
            return result;
        } catch (RuntimeException failure) {
            recordDuration(operation, startedAt);
            recordError(operation, failure);
            throw failure;
        }
    }

    public void run(String operation, Runnable action) {
        call(operation, () -> {
            action.run();
            return null;
        });
    }

    private void recordDuration(String operation, Instant startedAt) {
        double seconds = Duration.between(startedAt, Instant.now()).toNanos() / 1_000_000_000.0;
        observabilityPort.recordDuration(new MetricSample("egen_adapter_call_duration_seconds", seconds,
                Map.of("adapter", adapterName, "operation", operation)));
    }

    private void recordError(String operation, RuntimeException failure) {
        observabilityPort.incrementCounter("egen_adapter_call_errors_total",
                Map.of("adapter", adapterName, "operation", operation,
                        "reason", failure.getClass().getSimpleName()));
    }
}
