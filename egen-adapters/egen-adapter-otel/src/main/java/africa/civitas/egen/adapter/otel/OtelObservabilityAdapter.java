package africa.civitas.egen.adapter.otel;

import africa.civitas.egen.application.port.MetricSample;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.ReconciliationEvent;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.port.TraceSpan;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ConditionStatus;
import africa.civitas.egen.domain.model.ServiceId;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation du {@link ObservabilityPort} via le SDK OpenTelemetry reel,
 * export OTLP (voir docs/architecture/07-ports-et-adapters.md,
 * "Observability Port" et docs/architecture/15-observabilite.md). Seul
 * point du Kernel a connaitre OpenTelemetry — la boucle de reconciliation
 * ne manipule jamais un type OTel directement (garde-fou n2,
 * docs/architecture/02-principes-fondamentaux.md) : le contrat neutre
 * {@link TraceContext} transporte un simple {@code traceparent} W3C.
 */
public final class OtelObservabilityAdapter implements ObservabilityPort, AutoCloseable {

    private static final System.Logger STRUCTURED_LOG = System.getLogger("egen.reconciliation");
    private static final String INSTRUMENTATION_SCOPE = "egen-kernel";

    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private static final TextMapSetter<Map<String, String>> SETTER =
            (carrier, key, value) -> carrier.put(key, value);

    private final OpenTelemetrySdk sdk;
    private final Tracer tracer;
    private final Meter meter;
    private final TextMapPropagator propagator = W3CTraceContextPropagator.getInstance();
    private final Map<String, DoubleHistogram> histograms = new ConcurrentHashMap<>();
    private final Map<String, LongCounter> counters = new ConcurrentHashMap<>();

    /**
     * Conserve pour compatibilite (tests, usages simples) — delegue avec des
     * attributs de ressource neutres. Preferer le constructeur complet en
     * production (voir docs/architecture/07-ports-et-adapters.md,
     * "Observability Port" : conventions semantiques standard).
     */
    public OtelObservabilityAdapter(String otlpEndpoint, String serviceName) {
        this(otlpEndpoint, serviceName, "0.0.0-unknown", "egen", "development");
    }

    /**
     * @param serviceVersion       {@code service.version} OTel — version du
     *                             Kernel lui-meme (ex. {@code quarkus.application.version}),
     *                             jamais la version d'un service metier deploye.
     * @param serviceNamespace     {@code service.namespace} OTel — regroupement logique
     *                             (ex. l'organisation ou le domaine hebergeant ce Kernel).
     * @param deploymentEnvironment {@code deployment.environment} OTel (ex. "production",
     *                             "staging") — voir {@link africa.civitas.egen.domain.model.TargetEnvironment}
     *                             pour l'environnement CIBLE d'un service deploye, une notion
     *                             distincte de l'environnement d'execution du Kernel lui-meme.
     */
    public OtelObservabilityAdapter(String otlpEndpoint, String serviceName, String serviceVersion,
                                     String serviceNamespace, String deploymentEnvironment) {
        Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"), serviceName,
                AttributeKey.stringKey("service.version"), serviceVersion,
                AttributeKey.stringKey("service.namespace"), serviceNamespace,
                AttributeKey.stringKey("deployment.environment"), deploymentEnvironment)));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(
                        OtlpGrpcSpanExporter.builder().setEndpoint(otlpEndpoint).build()).build())
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(PeriodicMetricReader.builder(
                        OtlpGrpcMetricExporter.builder().setEndpoint(otlpEndpoint).build()).build())
                .build();

        this.sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .build();
        this.tracer = sdk.getTracer(INSTRUMENTATION_SCOPE);
        this.meter = sdk.getMeter(INSTRUMENTATION_SCOPE);
    }

    @Override
    public void close() {
        sdk.close();
    }

    @Override
    public TraceSpan startSpan(String operationName, TraceContext parent) {
        Context parentContext = Context.root();
        if (parent != null) {
            Map<String, String> carrier = Map.of("traceparent", parent.traceparent());
            parentContext = propagator.extract(Context.root(), carrier, GETTER);
        }
        Span span = tracer.spanBuilder(operationName).setParent(parentContext).startSpan();
        return new OtelTraceSpan(span);
    }

    @Override
    public void recordDuration(MetricSample sample) {
        DoubleHistogram histogram = histograms.computeIfAbsent(sample.name(),
                name -> meter.histogramBuilder(name).build());
        histogram.record(sample.value(), toAttributes(sample.attributes()));
    }

    @Override
    public void incrementCounter(String name, Map<String, String> attributes) {
        LongCounter counter = counters.computeIfAbsent(name, n -> meter.counterBuilder(n).build());
        counter.add(1, toAttributes(attributes));
    }

    @Override
    public void recordEvent(ReconciliationEvent event) {
        Span current = Span.current();
        String traceId = current.getSpanContext().isValid() ? current.getSpanContext().getTraceId() : "none";
        if (current.getSpanContext().isValid()) {
            current.addEvent(event.message(), Attributes.of(
                    AttributeKey.stringKey("operation.id"), event.operationId(),
                    AttributeKey.stringKey("service.id"), event.serviceId().value(),
                    AttributeKey.longKey("generation"), event.generation()));
        }
        // Log structure toujours enrichi de serviceId/generation/operationId/traceId
        // (voir docs/architecture/15-observabilite.md, "Les trois signaux").
        STRUCTURED_LOG.log(System.Logger.Level.INFO,
                "operationId={0} serviceId={1} generation={2} traceId={3} message=\"{4}\"",
                event.operationId(), event.serviceId().value(), event.generation(), traceId, event.message());
    }

    @Override
    public void reportCondition(ServiceId id, Condition condition) {
        Span current = Span.current();
        String traceId = current.getSpanContext().isValid() ? current.getSpanContext().getTraceId() : "none";
        if (current.getSpanContext().isValid()) {
            current.addEvent("condition:" + condition.type(), Attributes.of(
                    AttributeKey.stringKey("condition.status"), condition.status().name(),
                    AttributeKey.stringKey("condition.reason"), condition.reason()));
        }
        System.Logger.Level level = condition.status() == ConditionStatus.FALSE
                ? System.Logger.Level.WARNING
                : System.Logger.Level.INFO;
        STRUCTURED_LOG.log(level, "serviceId={0} traceId={1} condition={2} status={3} reason=\"{4}\" message=\"{5}\"",
                id.value(), traceId, condition.type(), condition.status(), condition.reason(), condition.message());
    }

    private static Attributes toAttributes(Map<String, String> attributes) {
        AttributesBuilder builder = Attributes.builder();
        attributes.forEach((key, value) -> builder.put(AttributeKey.stringKey(key), value));
        return builder.build();
    }

    /**
     * Enveloppe neutre d'un span OTel actif. Le constructeur rend
     * immediatement ce span "courant" ({@link Span#makeCurrent()}) pour que
     * {@link #recordEvent}/{@link #reportCondition} puissent y attacher des
     * evenements via {@code Span.current()} sans avoir a threader le
     * contexte partout — {@link #close()} restaure le contexte precedent.
     */
    private final class OtelTraceSpan implements TraceSpan {

        private final Span span;
        private final Scope scope;

        OtelTraceSpan(Span span) {
            this.span = span;
            this.scope = span.makeCurrent();
        }

        @Override
        public TraceContext context() {
            Map<String, String> carrier = new HashMap<>();
            propagator.inject(Context.current().with(span), carrier, SETTER);
            return new TraceContext(carrier.get("traceparent"));
        }

        @Override
        public void setAttribute(String key, String value) {
            span.setAttribute(key, value);
        }

        @Override
        public void recordError(Throwable error) {
            span.recordException(error);
            span.setStatus(StatusCode.ERROR, error.getMessage() == null ? "" : error.getMessage());
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
