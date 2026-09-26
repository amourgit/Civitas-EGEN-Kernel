package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.MetricSample;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.ReconciliationEvent;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.port.TraceSpan;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.model.ServiceId;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Double de test EN MEMOIRE, prive a egen-application : n'exporte rien
 * reellement, se contente de compter les appels pour les quelques tests qui
 * veulent verifier qu'une erreur a bien ete rapportee sur le span courant.
 */
final class NoOpObservabilityPort implements ObservabilityPort {

    final AtomicInteger spansStarted = new AtomicInteger();
    final AtomicInteger errorsRecorded = new AtomicInteger();

    @Override
    public TraceSpan startSpan(String operationName, TraceContext parent) {
        spansStarted.incrementAndGet();
        return new TraceSpan() {
            @Override
            public TraceContext context() {
                return new TraceContext("00-0000000000000000000000000000000-0000000000000000-01");
            }

            @Override
            public void setAttribute(String key, String value) {
            }

            @Override
            public void recordError(Throwable error) {
                errorsRecorded.incrementAndGet();
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public void recordDuration(MetricSample sample) {
    }

    @Override
    public void incrementCounter(String name, Map<String, String> attributes) {
    }

    @Override
    public void recordEvent(ReconciliationEvent event) {
    }

    @Override
    public void reportCondition(ServiceId id, Condition condition) {
    }
}
