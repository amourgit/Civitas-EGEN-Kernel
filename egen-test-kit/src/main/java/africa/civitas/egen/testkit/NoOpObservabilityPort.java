package africa.civitas.egen.testkit;

import africa.civitas.egen.application.port.MetricSample;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.ReconciliationEvent;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.port.TraceSpan;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.Map;

/**
 * Double de test NO-OP d'un {@link ObservabilityPort}, pour les modules
 * AVAL d'egen-application (voir la description d'egen-test-kit). N'envoie
 * aucune telemetrie nulle part — utile uniquement pour exercer un code
 * appelant sans exiger un backend OTel reel dans les tests.
 */
public final class NoOpObservabilityPort implements ObservabilityPort {

    @Override
    public TraceSpan startSpan(String operationName, TraceContext parent) {
        return new NoOpTraceSpan(parent);
    }

    @Override
    public void recordDuration(MetricSample sample) {
        // no-op
    }

    @Override
    public void incrementCounter(String name, Map<String, String> attributes) {
        // no-op
    }

    @Override
    public void recordEvent(ReconciliationEvent event) {
        // no-op
    }

    @Override
    public void reportCondition(ServiceId id, Condition condition) {
        // no-op
    }

    private record NoOpTraceSpan(TraceContext parent) implements TraceSpan {

        @Override
        public TraceContext context() {
            return parent != null ? parent : new TraceContext("00-00000000000000000000000000000000-0000000000000000-01");
        }

        @Override
        public void setAttribute(String key, String value) {
            // no-op
        }

        @Override
        public void recordError(Throwable error) {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
