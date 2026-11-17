package africa.civitas.egen.application.workflow;

import java.util.Optional;
import africa.civitas.egen.application.port.InvocationResult;
import africa.civitas.egen.application.workflow.EngineFakes.CountingObservability;
import africa.civitas.egen.application.workflow.EngineFakes.FakeDiscovery;
import africa.civitas.egen.application.workflow.EngineFakes.FakeMessaging;
import africa.civitas.egen.application.workflow.EngineFakes.MutableClock;
import africa.civitas.egen.application.workflow.EngineFakes.ScriptedInvoker;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.workflow.CompensationStrategy;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.ExecutionPolicy;
import africa.civitas.egen.domain.workflow.IllegalExecutionTransitionException;
import africa.civitas.egen.domain.workflow.RetryPolicy;
import africa.civitas.egen.domain.workflow.RetryPolicy.BackoffStrategy;
import africa.civitas.egen.domain.workflow.Step;
import africa.civitas.egen.domain.workflow.StepId;
import africa.civitas.egen.domain.workflow.StepOperation;
import africa.civitas.egen.domain.workflow.StepResult;
import africa.civitas.egen.domain.workflow.StepStatus;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowId;
import africa.civitas.egen.domain.workflow.WorkflowInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static africa.civitas.egen.application.workflow.EngineFakes.ScriptedInvoker.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Niveau 2 (docs/architecture/17) : bail de pilotage des executions (docs/architecture/11, « Bail d'execution » ;
 * phase P0, etape P0-3b). Plusieurs moteurs, comme plusieurs exemplaires du Kernel, partagent un meme store.
 */
class WorkflowEngineLeaseTest {
    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final ServiceId NOTIF = ServiceId.of("notification-service");
    private static final Instant T0 = Instant.parse("2026-11-17T10:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(60);

    private InMemoryWorkflowStore store;
    private ScriptedInvoker invoker;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        invoker = new ScriptedInvoker();
        clock = new MutableClock(T0);
    }

    private WorkflowEngine engine(String driverId) {
        return new WorkflowEngine(store, new FakeDiscovery(), invoker, new FakeMessaging(), clock, d -> { },
                Runnable::run, new WorkflowEngineSettings(Duration.ofMillis(100), Duration.ofSeconds(30)))
                .lease(driverId, LEASE);
    }

    private static WorkflowDefinition definition() {
        return WorkflowDefinition.of(WorkflowId.of("publish-article-workflow"), List.of(
                new Step(StepId.of("validate-content"), NEWS, StepOperation.parse("POST /internal/validate"), List.of(),
                        Duration.ofSeconds(10), RetryPolicy.none(), null),
                new Step(StepId.of("publish"), NEWS, StepOperation.parse("POST /internal/publish"),
                        List.of(StepId.of("validate-content")), Duration.ofSeconds(10), RetryPolicy.none(), null)),
                CompensationStrategy.BACKWARD, ExecutionPolicy.sequential(Duration.ofMinutes(5)));
    }

    private ExecutionId seedRunning() {
        WorkflowDefinition definition = definition();
        store.saveDefinition(definition);
        ExecutionId id = ExecutionId.generate();
        store.save(WorkflowExecution.rehydrate(definition.id(), id, WorkflowExecutionStatus.RUNNING, List.of(), T0,
                Map.of("articleId", "42")));
        return id;
    }

    @Test
    void anExecutionHeldByAnotherKernelIsNotDriven() {
        ExecutionId id = seedRunning();
        assertTrue(store.tryClaim(id, "kernel-a", T0, T0.plus(LEASE)));

        engine("kernel-b").recoverInterruptedExecutions();

        assertEquals(WorkflowExecutionStatus.RUNNING, store.findById(id).orElseThrow().status());
        assertTrue(invoker.calls.isEmpty(), "aucune etape ne doit etre appelee par un Kernel sans bail");
        assertEquals("kernel-a", store.leaseHolder(id).orElseThrow(), "le bail de l'autre Kernel est intact");
    }

    @Test
    void anExecutionWhoseLeaseExpiredIsTakenOverAndCompleted() {
        ExecutionId id = seedRunning();
        store.tryClaim(id, "kernel-a", T0, T0.plus(LEASE)); // kernel-a est mort sans rendre son bail
        clock.advance(LEASE.plusSeconds(1));

        engine("kernel-b").recoverInterruptedExecutions();

        assertEquals(WorkflowExecutionStatus.COMPLETED, store.findById(id).orElseThrow().status());
        assertEquals(List.of("POST /internal/validate", "POST /internal/publish"), invoker.keys());
    }

    @Test
    void theLeaseIsHeldByTheDrivingKernelDuringAStepAndReleasedAtTheEnd() {
        ExecutionId id = seedRunning();
        List<Optional<String>> holdersDuringSteps = new ArrayList<>();
        invoker.before = call -> holdersDuringSteps.add(store.leaseHolder(id));

        engine("kernel-b").recoverInterruptedExecutions();

        assertEquals(List.of(Optional.of("kernel-b"), Optional.of("kernel-b")), holdersDuringSteps);
        assertTrue(store.leaseHolder(id).isEmpty(), "le bail est rendu quand l'execution se termine");
    }

    @Test
    void twoKernelsRecoveringTheSameExecutionCallEachStepExactlyOnce() {
        ExecutionId id = seedRunning();
        WorkflowEngine a = engine("kernel-a");
        WorkflowEngine b = engine("kernel-b");
        // Pendant la premiere etape de A, B tente de reprendre la meme execution.
        invoker.before = call -> {
            if (call.path().equals("/internal/validate")) {
                b.recoverInterruptedExecutions();
            }
        };

        a.recoverInterruptedExecutions();

        assertEquals(WorkflowExecutionStatus.COMPLETED, store.findById(id).orElseThrow().status());
        assertEquals(List.of("POST /internal/validate", "POST /internal/publish"), invoker.keys(),
                "chaque etape est appelee une seule fois malgre deux pilotes candidats");
    }

    @Test
    void aTerminalOrPausedExecutionTakesNoLeaseBecauseItIsNeverDriven() {
        WorkflowDefinition definition = definition();
        store.saveDefinition(definition);
        ExecutionId paused = ExecutionId.generate();
        store.save(WorkflowExecution.rehydrate(definition.id(), paused, WorkflowExecutionStatus.PAUSED, List.of(), T0,
                Map.of()));

        engine("kernel-a").recoverInterruptedExecutions();

        assertTrue(store.leaseHolder(paused).isEmpty());
    }

    @Test
    void theLeaseRulesOfTheStoreDouble() {
        ExecutionId id = ExecutionId.generate();
        assertTrue(store.tryClaim(id, "a", T0, T0.plusSeconds(60)), "libre : obtenu");
        assertFalse(store.tryClaim(id, "b", T0.plusSeconds(10), T0.plusSeconds(70)), "valide chez a : refuse");
        assertTrue(store.tryClaim(id, "a", T0.plusSeconds(10), T0.plusSeconds(70)), "deja a a : accepte");
        assertTrue(store.tryClaim(id, "b", T0.plusSeconds(70), T0.plusSeconds(130)), "expire : repris par b");
        assertFalse(store.renew(id, "a", T0.plusSeconds(71), T0.plusSeconds(131)), "a n'est plus detenteur");
        assertTrue(store.renew(id, "b", T0.plusSeconds(71), T0.plusSeconds(131)), "b prolonge");
        store.release(id, "a");
        assertEquals("b", store.leaseHolder(id).orElseThrow(), "a ne peut pas rendre le bail de b");
        store.release(id, "b");
        assertTrue(store.leaseHolder(id).isEmpty());
        assertFalse(store.renew(id, "b", T0.plusSeconds(80), T0.plusSeconds(140)), "rien a prolonger");
    }

    @Test
    void anInvalidLeaseConfigurationIsRefused() {
        WorkflowEngine engine = engine("kernel-a");
        assertThrows(IllegalArgumentException.class, () -> engine.lease(" ", LEASE));
        assertThrows(IllegalArgumentException.class, () -> engine.lease("k", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> engine.lease("k", Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> engine.lease("k", null));
    }
}
