package africa.civitas.egen.application.workflow;

import africa.civitas.egen.application.resilience.ResiliencyPolicyProvider;
import africa.civitas.egen.application.workflow.EngineFakes.FakeDiscovery;
import africa.civitas.egen.application.workflow.EngineFakes.FakeMessaging;
import africa.civitas.egen.application.workflow.EngineFakes.MutableClock;
import africa.civitas.egen.application.workflow.EngineFakes.ScriptedInvoker;
import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ResiliencyPolicy.CircuitBreakerSpec;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.workflow.CompensationStrategy;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.ExecutionPolicy;
import africa.civitas.egen.domain.workflow.RetryPolicy;
import africa.civitas.egen.domain.workflow.RetryPolicy.BackoffStrategy;
import africa.civitas.egen.domain.workflow.Step;
import africa.civitas.egen.domain.workflow.StepId;
import africa.civitas.egen.domain.workflow.StepOperation;
import africa.civitas.egen.domain.workflow.StepResult;
import africa.civitas.egen.domain.workflow.StepStatus;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowId;
import africa.civitas.egen.domain.workflow.WorkflowInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static africa.civitas.egen.application.workflow.EngineFakes.ScriptedInvoker.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resilience de la cible dans le Moteur de Workflow (docs/architecture/12,
 * "Politique appliquee par le Moteur de Workflow") : circuit breaker partage
 * et timeout — jamais le retry de la cible, deja porte par l'etape.
 */
class WorkflowEngineResilienceTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final ServiceId NOTIF = ServiceId.of("notification-service");
    private static final Duration BACKOFF = Duration.ofMillis(100);

    private InMemoryWorkflowStore store;
    private ScriptedInvoker invoker;
    private FakeDiscovery discovery;
    private MutableClock clock;
    private List<Duration> sleeps;
    private Map<ServiceId, ResiliencyPolicy> policies;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        invoker = new ScriptedInvoker();
        discovery = new FakeDiscovery();
        clock = new MutableClock(Instant.parse("2026-10-09T10:00:00Z"));
        sleeps = new ArrayList<>();
        policies = new HashMap<>();
        engine = new WorkflowEngine(store, discovery, invoker, new FakeMessaging(), clock,
                duration -> {
                    sleeps.add(duration);
                    clock.advance(duration); // le temps simule avance reellement pendant l'attente
                }, Runnable::run, new WorkflowEngineSettings(BACKOFF, Duration.ofSeconds(30)),
                (ResiliencyPolicyProvider) target -> policies.getOrDefault(target, ResiliencyPolicy.defaults()));
    }

    private static ResiliencyPolicy breaker(int threshold, Duration open) {
        return new ResiliencyPolicy(null, null, new CircuitBreakerSpec(threshold, open));
    }

    private static Step step(String id, ServiceId service, String operation, String compensation,
                             Duration timeout, RetryPolicy retry, String... dependsOn) {
        return new Step(StepId.of(id), service, StepOperation.parse(operation),
                java.util.Arrays.stream(dependsOn).map(StepId::of).toList(), timeout, retry,
                compensation == null ? null : StepOperation.parse(compensation));
    }

    private static WorkflowDefinition workflow(String name, Step... steps) {
        return WorkflowDefinition.of(WorkflowId.of(name), List.of(steps), CompensationStrategy.BACKWARD,
                ExecutionPolicy.sequential(Duration.ofMinutes(5)));
    }

    private static WorkflowDefinition single(String name, ServiceId service, RetryPolicy retry) {
        return workflow(name, step("only", service, "POST /x", null, Duration.ofSeconds(10), retry));
    }

    private ExecutionId run(WorkflowDefinition definition) {
        return engine.start(definition, WorkflowInput.empty());
    }

    private List<StepResult> results(ExecutionId id) {
        return store.findById(id).orElseThrow().stepResults();
    }

    // ------------------------------------------------------------------ circuit partage

    @Test
    void consecutiveFailuresOfOneTargetOpenItsCircuitForEveryLaterExecution() {
        policies.put(NEWS, breaker(2, Duration.ofMinutes(1)));
        invoker.respond("POST /x", status(503), status(503));

        run(single("wf-1", NEWS, RetryPolicy.none()));
        run(single("wf-2", NEWS, RetryPolicy.none()));
        assertEquals(2, invoker.calls.size());

        ExecutionId third = run(single("wf-3", NEWS, RetryPolicy.none()));

        assertEquals(2, invoker.calls.size(), "circuit ouvert : aucun nouvel appel reseau");
        assertEquals(WorkflowExecutionStatus.FAILED, engine.status(third));
        assertTrue(results(third).get(0).errorMessage().contains("Circuit ouvert"));
    }

    @Test
    void theCircuitOfOneTargetNeverAffectsAnother() {
        policies.put(NEWS, breaker(1, Duration.ofMinutes(1)));
        invoker.respond("POST /x", status(503));
        run(single("wf-1", NEWS, RetryPolicy.none()));

        ExecutionId other = run(single("wf-2", NOTIF, RetryPolicy.none()));

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(other));
    }

    @Test
    void aDefinitiveClientErrorCountsAsASuccessForTheCircuit() {
        policies.put(NEWS, breaker(2, Duration.ofMinutes(1)));
        invoker.respond("POST /x", status(404), status(404), status(404), status(404));

        for (int i = 1; i <= 4; i++) {
            run(single("wf-" + i, NEWS, RetryPolicy.none()));
        }

        assertEquals(4, invoker.calls.size(), "des 404 repetes n'ouvrent jamais le circuit");
    }

    @Test
    void aNetworkErrorCountsAsAFailure() {
        policies.put(NEWS, breaker(2, Duration.ofMinutes(1)));
        invoker.respond("POST /x", new UncheckedIOException(new IOException("refuse")),
                new UncheckedIOException(new IOException("refuse")));

        run(single("wf-1", NEWS, RetryPolicy.none()));
        run(single("wf-2", NEWS, RetryPolicy.none()));
        run(single("wf-3", NEWS, RetryPolicy.none()));

        assertEquals(2, invoker.calls.size());
    }

    @Test
    void anOpenCircuitIsARetryableFailureAndTheStepRecoversWhenTheWindowEnds() {
        policies.put(NEWS, breaker(1, Duration.ofMillis(150)));
        invoker.respond("POST /x", status(503), status(200));

        ExecutionId id = run(single("wf-1", NEWS, new RetryPolicy(4, BackoffStrategy.FIXED)));

        // t=0 : 503 -> circuit ouvert ; t=100 : rejete sans reseau ; t=200 : essai semi-ouvert -> 200
        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
        assertEquals(2, invoker.calls.size(), "la tentative rejetee n'a fait aucun appel reseau");
        assertEquals(List.of(StepStatus.FAILED, StepStatus.FAILED, StepStatus.SUCCEEDED),
                results(id).stream().map(StepResult::status).toList());
        assertTrue(results(id).get(1).errorMessage().contains("Circuit ouvert"));
        assertEquals(List.of(BACKOFF, BACKOFF), sleeps);
    }

    @Test
    void compensationsAreNeverRejectedByAnOpenCircuit() {
        policies.put(NEWS, breaker(1, Duration.ofMinutes(10)));
        invoker.respond("POST /c", status(503)); // c echoue -> ouvre le circuit de news-service
        ExecutionId id = run(workflow("wf-1",
                step("a", NEWS, "POST /a", "POST /undo-a", Duration.ofSeconds(10), RetryPolicy.none()),
                step("b", NEWS, "POST /b", "POST /undo-b", Duration.ofSeconds(10), RetryPolicy.none(), "a"),
                step("c", NEWS, "POST /c", "POST /undo-c", Duration.ofSeconds(10), RetryPolicy.none(), "b")));

        assertEquals(WorkflowExecutionStatus.COMPENSATED, engine.status(id));
        assertEquals(List.of("POST /a", "POST /b", "POST /c", "POST /undo-b", "POST /undo-a"), invoker.keys());
    }

    @Test
    void noHealthyInstanceNeverConsumesTheHalfOpenProbe() {
        policies.put(NEWS, breaker(1, Duration.ofSeconds(10)));
        invoker.respond("POST /x", status(503), status(200));
        run(single("wf-1", NEWS, RetryPolicy.none())); // ouvre le circuit
        clock.advance(Duration.ofSeconds(10));        // la fenetre est ecoulee : un essai serait admis

        discovery.makeUnavailable(NEWS);
        ExecutionId noInstance = run(single("wf-2", NEWS, RetryPolicy.none()));
        assertTrue(results(noInstance).get(0).errorMessage().contains("Aucune instance"));

        discovery.makeAvailable(NEWS);
        ExecutionId probe = run(single("wf-3", NEWS, RetryPolicy.none()));

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(probe),
                "l'essai semi-ouvert etait toujours disponible");
    }

    @Test
    void eventStepsDoNotGoThroughTheHttpCircuit() {
        policies.put(NOTIF, breaker(1, Duration.ofMinutes(1)));
        invoker.respond("POST /x", status(503));
        run(single("wf-1", NOTIF, RetryPolicy.none())); // ouvre le circuit HTTP de notification-service

        ExecutionId event = run(workflow("wf-2",
                step("notify", NOTIF, "EVENT africa.civitas.x.v1", null, Duration.ofSeconds(10), RetryPolicy.none())));

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(event));
    }

    // ------------------------------------------------------------------ timeout

    @Test
    void theStrictestOfTheStepAndTheTargetTimeoutIsUsed() {
        policies.put(NEWS, new ResiliencyPolicy(Duration.ofSeconds(5), null, null));
        run(workflow("wf-1", step("short-target", NEWS, "POST /a", null, Duration.ofSeconds(10), RetryPolicy.none())));
        policies.put(NOTIF, new ResiliencyPolicy(Duration.ofSeconds(20), null, null));
        run(workflow("wf-2", step("short-step", NOTIF, "POST /b", null, Duration.ofSeconds(10), RetryPolicy.none())));

        assertEquals(Duration.ofSeconds(5), invoker.calls.get(0).timeout());
        assertEquals(Duration.ofSeconds(10), invoker.calls.get(1).timeout());
    }

    @Test
    void theTargetsRetryIsNeverAppliedOnTopOfTheStepsRetry() {
        policies.put(NEWS, new ResiliencyPolicy(null,
                new ResiliencyPolicy.RetrySpec(5, ResiliencyPolicy.Backoff.FIXED, Duration.ofMillis(1)),
                new CircuitBreakerSpec(100, Duration.ofSeconds(1))));
        invoker.respond("POST /x", status(503), status(503), status(503), status(503), status(503), status(503));

        run(single("wf-1", NEWS, new RetryPolicy(2, BackoffStrategy.FIXED)));

        assertEquals(2, invoker.calls.size(), "seul le retry de l'etape s'applique (pas de retries multiplies)");
    }
}
