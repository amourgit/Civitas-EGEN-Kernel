package africa.civitas.egen.adapter.otel;

import africa.civitas.egen.application.port.MetricSample;
import africa.civitas.egen.application.port.ReconciliationEvent;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.port.TraceSpan;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ConditionStatus;
import africa.civitas.egen.domain.model.ServiceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la logique de propagation de {@link OtelObservabilityAdapter},
 * sans dependre d'un vrai collecteur OTLP joignable — voir
 * docs/architecture/17-strategie-de-tests.md, niveau 2/3 : l'export reel
 * vers Jaeger/Prometheus (docs/architecture/20-scenario-bout-en-bout.md)
 * s'observe en environnement dedie, pas ici. Un point de terminaison OTLP
 * injoignable n'empeche pas ces assertions : les exporteurs OTLP echouent
 * en arriere-plan sans jamais bloquer l'appelant.
 */
class OtelObservabilityAdapterTest {

    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");

    private OtelObservabilityAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OtelObservabilityAdapter("http://localhost:4317", "egen-kernel-test");
    }

    @AfterEach
    void tearDown() {
        adapter.close();
    }

    @Test
    void startSpanProducesAWellFormedW3cTraceparent() {
        try (TraceSpan span = adapter.startSpan("reconcile", null)) {
            assertTrue(TRACEPARENT_PATTERN.matcher(span.context().traceparent()).matches());
        }
    }

    @Test
    void aChildSpanSharesTheSameTraceIdAsItsParent() {
        try (TraceSpan root = adapter.startSpan("reconcile", null)) {
            TraceContext parentContext = root.context();
            try (TraceSpan child = adapter.startSpan("deployment.create", parentContext)) {
                String parentTraceId = parentContext.traceparent().split("-")[1];
                String childTraceId = child.context().traceparent().split("-")[1];
                assertEquals(parentTraceId, childTraceId);
            }
        }
    }

    @Test
    void recordingMetricsAndEventsNeverThrowsEvenWithNoReachableCollector() {
        assertDoesNotThrow(() -> {
            adapter.recordDuration(new MetricSample("egen_reconcile_duration_seconds", 0.42,
                    Map.of("service", "news-service")));
            adapter.incrementCounter("egen_services_total", Map.of("phase", "RUNNING"));
            adapter.recordEvent(new ReconciliationEvent(UUID.randomUUID().toString(),
                    ServiceId.of("news-service"), "reconcile phase=RUNNING", Instant.now()));
            adapter.reportCondition(ServiceId.of("news-service"),
                    new Condition("DeploymentReady", ConditionStatus.TRUE, "Converged", "ok", Instant.now()));
        });
    }
}
