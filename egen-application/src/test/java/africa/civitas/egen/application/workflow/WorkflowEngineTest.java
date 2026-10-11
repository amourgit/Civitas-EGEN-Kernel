package africa.civitas.egen.application.workflow;

import africa.civitas.egen.application.port.InvocationResult;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 1-2 (voir docs/architecture/17-strategie-de-tests.md) : le moteur
 * contre des doubles de ports — aucune I/O. Chaque test correspond a une
 * regle de docs/architecture/11-moteur-de-workflow.md, "Moteur d'execution".
 */
class WorkflowEngineTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final ServiceId NOTIF = ServiceId.of("notification-service");
    private static final Duration BASE_BACKOFF = Duration.ofMillis(100);
    private static final Instant T0 = Instant.parse("2026-10-09T10:00:00Z");

    private InMemoryWorkflowStore store;
    private ScriptedInvoker invoker;
    private FakeDiscovery discovery;
    private FakeMessaging messaging;
    private MutableClock clock;
    private List<Duration> sleeps;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStore();
        invoker = new ScriptedInvoker();
        discovery = new FakeDiscovery();
        messaging = new FakeMessaging();
        clock = new MutableClock(T0);
        sleeps = new ArrayList<>();
        engine = newEngine(sleeps::add);
    }

    private WorkflowEngine newEngine(Sleeper sleeper) {
        return new WorkflowEngine(store, discovery, invoker, messaging, clock, sleeper, Runnable::run,
                new WorkflowEngineSettings(BASE_BACKOFF, Duration.ofSeconds(30)));
    }

    // ------------------------------------------------------------------ fixtures

    private static Step step(String id, ServiceId service, String operation, String compensation,
                             RetryPolicy retry, String... dependsOn) {
        return new Step(StepId.of(id), service, StepOperation.parse(operation),
                java.util.Arrays.stream(dependsOn).map(StepId::of).toList(), Duration.ofSeconds(10), retry,
                compensation == null ? null : StepOperation.parse(compensation));
    }

    private static WorkflowDefinition workflow(ExecutionPolicy policy, Step... steps) {
        return WorkflowDefinition.of(WorkflowId.of("publish-article-workflow"), List.of(steps),
                CompensationStrategy.BACKWARD, policy);
    }

    private static WorkflowDefinition workflow(Step... steps) {
        return workflow(ExecutionPolicy.sequential(Duration.ofMinutes(5)), steps);
    }

    /** Le workflow de reference de docs/architecture/11 (publish-article-workflow). */
    private static WorkflowDefinition publishArticle(ExecutionPolicy policy) {
        return workflow(policy,
                step("validate-content", NEWS, "POST /internal/validate", null,
                        new RetryPolicy(3, BackoffStrategy.EXPONENTIAL)),
                step("reserve-notification-slot", NOTIF, "POST /internal/reserve-slot",
                        "POST /internal/release-slot", RetryPolicy.none(), "validate-content"),
                step("publish", NEWS, "POST /internal/publish", "POST /internal/unpublish",
                        RetryPolicy.none(), "reserve-notification-slot"),
                step("notify-subscribers", NOTIF, "EVENT africa.civitas.news.article.published.v1", null,
                        RetryPolicy.none(), "publish"));
    }

    private static WorkflowDefinition publishArticle() {
        return publishArticle(ExecutionPolicy.sequential(Duration.ofMinutes(5)));
    }

    private ExecutionId run(WorkflowDefinition definition) {
        return engine.start(definition, new WorkflowInput(Map.of("articleId", "42")));
    }

    private WorkflowExecution execution(ExecutionId id) {
        return store.findById(id).orElseThrow();
    }

    private List<StepStatus> statusesOf(ExecutionId id, String stepId) {
        return execution(id).stepResults().stream()
                .filter(r -> r.stepId().equals(StepId.of(stepId))).map(StepResult::status).toList();
    }

    @org.junit.jupiter.api.AfterEach
    void noFactWasEverSilentlyLost() {
        assertEquals(0, store.droppedConflictingResults,
                "un StepResult a ete ecrit sur une cle (etape, tentative) deja prise : "
                        + "il serait perdu en base (ON CONFLICT DO NOTHING)");
    }

    // ------------------------------------------------------------------ chemin nominal

    @Test
    void runsTheReferenceWorkflowToCompletionInDependencyOrder() {
        ExecutionId id = run(publishArticle());

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
        assertEquals(List.of("POST /internal/validate", "POST /internal/reserve-slot", "POST /internal/publish"),
                invoker.keys());
        assertEquals(4, execution(id).succeededStepsInExecutionOrder().size());
        assertEquals(List.of("validate-content", "reserve-notification-slot", "publish", "notify-subscribers"),
                execution(id).succeededStepsInExecutionOrder().stream().map(StepId::value).toList());
    }

    @Test
    void anEventStepPublishesACloudEventCorrelatedToTheExecutionWithJsonData() {
        ExecutionId id = run(publishArticle());

        assertEquals(1, messaging.published.size());
        var published = messaging.published.get(0);
        assertEquals("africa.civitas.news.article.published.v1", published.subject());
        assertEquals("africa.civitas.news.article.published.v1", published.event().type());
        assertEquals(id.value(), published.event().correlationId());
        assertEquals("{\"articleId\":\"42\"}", published.event().data());
        assertEquals("/egen/workflows/publish-article-workflow", published.event().source());
    }

    @Test
    void bodyIsTheWorkflowInputForPostButEmptyForGetAndDelete() {
        run(workflow(
                step("read", NEWS, "GET /internal/read", null, RetryPolicy.none()),
                step("write", NEWS, "POST /internal/write", null, RetryPolicy.none(), "read"),
                step("drop", NEWS, "DELETE /internal/drop", null, RetryPolicy.none(), "write")));

        assertEquals(Map.of(), invoker.calls.get(0).payload());
        assertEquals(Map.of("articleId", "42"), invoker.calls.get(1).payload());
        assertEquals(Map.of(), invoker.calls.get(2).payload());
    }

    @Test
    void theStepTimeoutAndTheResolvedInstanceAreUsedForTheCall() {
        run(workflow(step("only", NOTIF, "POST /x", null, RetryPolicy.none())));

        assertEquals(Duration.ofSeconds(10), invoker.calls.get(0).timeout());
        assertEquals("notification-service:8080", invoker.calls.get(0).service());
    }

    @Test
    void stepOutputIsJournaledFromTheResponseBody() {
        invoker.respond("POST /x", new InvocationResult(201, Map.of("id", "abc")));
        ExecutionId id = run(workflow(step("only", NEWS, "POST /x", null, RetryPolicy.none())));

        assertEquals(Map.of("id", "abc"), execution(id).latestResultFor(StepId.of("only")).orElseThrow().output());
    }

    // ------------------------------------------------------------------ retry

    @Test
    void retriesOn5xxWithExponentialBackoffThenSucceeds() {
        invoker.respond("POST /internal/validate", status(503), status(503), status(200));
        ExecutionId id = run(publishArticle());

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
        assertEquals(List.of(StepStatus.FAILED, StepStatus.FAILED, StepStatus.SUCCEEDED),
                statusesOf(id, "validate-content"));
        assertEquals(List.of(1, 2, 3), execution(id).stepResults().stream()
                .filter(r -> r.stepId().equals(StepId.of("validate-content"))).map(StepResult::attempt).toList());
        assertEquals(List.of(BASE_BACKOFF, BASE_BACKOFF.multipliedBy(2)), sleeps);
    }

    @Test
    void fixedBackoffAlwaysWaitsTheBaseDelay() {
        invoker.respond("POST /x", status(500), status(500), status(200));
        run(workflow(step("only", NEWS, "POST /x", null, new RetryPolicy(3, BackoffStrategy.FIXED))));

        assertEquals(List.of(BASE_BACKOFF, BASE_BACKOFF), sleeps);
    }

    @Test
    void backoffIsCappedByTheConfiguredMaximum() {
        WorkflowEngine capped = new WorkflowEngine(store, discovery, invoker, messaging, clock, sleeps::add,
                Runnable::run, new WorkflowEngineSettings(Duration.ofSeconds(10), Duration.ofSeconds(25)));
        invoker.respond("POST /x", status(500), status(500), status(500), status(200));
        capped.start(workflow(step("only", NEWS, "POST /x", null, new RetryPolicy(4, BackoffStrategy.EXPONENTIAL))),
                WorkflowInput.empty());

        assertEquals(List.of(Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(25)), sleeps);
    }

    @Test
    void exhaustedRetriesFailTheExecutionWithoutWaitingAfterTheLastAttempt() {
        invoker.respond("POST /internal/validate", status(503), status(503), status(503));
        ExecutionId id = run(publishArticle());

        assertEquals(WorkflowExecutionStatus.FAILED, engine.status(id)); // rien a compenser
        assertEquals(List.of(StepStatus.FAILED, StepStatus.FAILED, StepStatus.FAILED),
                statusesOf(id, "validate-content"));
        assertEquals(2, sleeps.size());
        assertEquals(1, invoker.calls.stream().map(EngineFakes.Call::key).distinct().count());
    }

    @Test
    void aClientErrorIsNeverRetried() {
        invoker.respond("POST /internal/validate", status(400));
        ExecutionId id = run(publishArticle());

        assertEquals(WorkflowExecutionStatus.FAILED, engine.status(id));
        assertEquals(List.of(StepStatus.FAILED), statusesOf(id, "validate-content"));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void timeoutAndTooManyRequestsAreRetryableClientErrors() {
        invoker.respond("POST /a", status(408), status(200));
        invoker.respond("POST /b", status(429), status(200));
        ExecutionId id = run(workflow(
                step("a", NEWS, "POST /a", null, new RetryPolicy(2, BackoffStrategy.FIXED)),
                step("b", NEWS, "POST /b", null, new RetryPolicy(2, BackoffStrategy.FIXED), "a")));

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
    }

    @Test
    void aNetworkErrorIsRetryable() {
        invoker.respond("POST /x", new UncheckedIOException(new IOException("connexion refusee")), status(200));
        ExecutionId id = run(workflow(step("only", NEWS, "POST /x", null, new RetryPolicy(2, BackoffStrategy.FIXED))));

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
        assertEquals(List.of(StepStatus.FAILED, StepStatus.SUCCEEDED), statusesOf(id, "only"));
        assertTrue(execution(id).stepResults().get(0).errorMessage().contains("connexion refusee"));
    }

    @Test
    void noHealthyInstanceIsRetryableThenFails() {
        discovery.makeUnavailable(NEWS);
        ExecutionId id = run(workflow(step("only", NEWS, "POST /x", null, new RetryPolicy(2, BackoffStrategy.FIXED))));

        assertEquals(WorkflowExecutionStatus.FAILED, engine.status(id));
        assertEquals(2, statusesOf(id, "only").size());
        assertTrue(execution(id).stepResults().get(0).errorMessage().contains("Aucune instance"));
        assertTrue(invoker.calls.isEmpty());
    }

    @Test
    void aFailingEventPublicationIsAnOrdinaryRetryableStepFailure() {
        messaging.failing = true;
        ExecutionId id = run(workflow(
                step("notify", NOTIF, "EVENT africa.civitas.x.v1", null, new RetryPolicy(2, BackoffStrategy.FIXED))));

        assertEquals(WorkflowExecutionStatus.FAILED, engine.status(id));
        assertEquals(2, statusesOf(id, "notify").size());
    }

    // ------------------------------------------------------------------ compensation

    private static WorkflowDefinition threeStepsWithCompensations() {
        return workflow(
                step("a", NEWS, "POST /a", "POST /undo-a", RetryPolicy.none()),
                step("b", NEWS, "POST /b", "POST /undo-b", RetryPolicy.none(), "a"),
                step("c", NEWS, "POST /c", "POST /undo-c", RetryPolicy.none(), "b"));
    }

    @Test
    void aFailureCompensatesSucceededStepsInReverseOrderAndNeverTheFailedStep() {
        invoker.respond("POST /c", status(500));
        ExecutionId id = run(threeStepsWithCompensations());

        assertEquals(WorkflowExecutionStatus.COMPENSATED, engine.status(id));
        assertEquals(List.of("POST /a", "POST /b", "POST /c", "POST /undo-b", "POST /undo-a"), invoker.keys());
        assertEquals(List.of(StepStatus.SUCCEEDED, StepStatus.COMPENSATED), statusesOf(id, "a"));
        assertEquals(List.of(StepStatus.FAILED), statusesOf(id, "c"));
    }

    @Test
    void referenceScenarioReleasesTheReservedSlotWhenPublishFails() {
        invoker.respond("POST /internal/publish", status(500));
        ExecutionId id = run(publishArticle());

        assertEquals(WorkflowExecutionStatus.COMPENSATED, engine.status(id));
        assertEquals(List.of("POST /internal/validate", "POST /internal/reserve-slot", "POST /internal/publish",
                "POST /internal/release-slot"), invoker.keys());
        assertTrue(messaging.published.isEmpty(), "l'evenement de notification ne doit jamais partir");
    }

    @Test
    void stepsWithoutACompensationAreSkippedDuringRollback() {
        invoker.respond("POST /c", status(500));
        ExecutionId id = run(workflow(
                step("a", NEWS, "POST /a", "POST /undo-a", RetryPolicy.none()),
                step("b", NEWS, "POST /b", null, RetryPolicy.none(), "a"),
                step("c", NEWS, "POST /c", "POST /undo-c", RetryPolicy.none(), "b")));

        assertEquals(WorkflowExecutionStatus.COMPENSATED, engine.status(id));
        assertEquals(List.of("POST /a", "POST /b", "POST /c", "POST /undo-a"), invoker.keys());
    }

    @Test
    void aFailedCompensationIsVisibleAndTheRemainingOnesAreStillAttempted() {
        invoker.respond("POST /c", status(500));
        invoker.respond("POST /undo-b", status(500));
        ExecutionId id = run(threeStepsWithCompensations());

        assertEquals(WorkflowExecutionStatus.COMPENSATION_FAILED, engine.status(id));
        assertEquals(List.of("POST /a", "POST /b", "POST /c", "POST /undo-b", "POST /undo-a"), invoker.keys());
        assertEquals(List.of(StepStatus.SUCCEEDED, StepStatus.COMPENSATION_FAILED), statusesOf(id, "b"));
        assertEquals(List.of(StepStatus.SUCCEEDED, StepStatus.COMPENSATED), statusesOf(id, "a"));
        assertTrue(execution(id).latestResultFor(StepId.of("b")).orElseThrow().errorMessage().contains("500"));
    }

    @Test
    void aFailureWithNothingToCompensateEndsInFailedNotCompensated() {
        invoker.respond("POST /a", status(500));
        ExecutionId id = run(threeStepsWithCompensations());

        assertEquals(WorkflowExecutionStatus.FAILED, engine.status(id));
        assertEquals(List.of("POST /a"), invoker.keys());
    }

    @Test
    void successfulStepsWithoutAnyCompensationLeaveTheExecutionFailedNotCompensated() {
        invoker.respond("POST /b", status(500));
        ExecutionId id = run(workflow(
                step("a", NEWS, "POST /a", null, RetryPolicy.none()),
                step("b", NEWS, "POST /b", "POST /undo-b", RetryPolicy.none(), "a")));

        assertEquals(WorkflowExecutionStatus.FAILED, engine.status(id));
        assertEquals(List.of("POST /a", "POST /b"), invoker.keys());
    }

    @Test
    void compensationOfAnEventStepPublishesItsCompensationEvent() {
        invoker.respond("POST /b", status(500));
        ExecutionId id = run(workflow(
                step("a", NOTIF, "EVENT africa.civitas.x.reserved.v1", "EVENT africa.civitas.x.released.v1",
                        RetryPolicy.none()),
                step("b", NEWS, "POST /b", null, RetryPolicy.none(), "a")));

        assertEquals(WorkflowExecutionStatus.COMPENSATED, engine.status(id));
        assertEquals(List.of("africa.civitas.x.reserved.v1", "africa.civitas.x.released.v1"),
                messaging.published.stream().map(p -> p.subject()).toList());
    }

    // ------------------------------------------------------------------ globalTimeout

    @Test
    void anExpiredGlobalTimeoutFailsTheNextStepAndCompensates() {
        ExecutionPolicy oneMinute = ExecutionPolicy.sequential(Duration.ofMinutes(1));
        invoker.before = call -> {
            if (call.key().equals("POST /internal/reserve-slot")) {
                clock.advance(Duration.ofMinutes(2));
            }
        };
        ExecutionId id = run(publishArticle(oneMinute));

        assertEquals(WorkflowExecutionStatus.COMPENSATED, engine.status(id));
        assertEquals(List.of("POST /internal/validate", "POST /internal/reserve-slot",
                "POST /internal/release-slot"), invoker.keys());
        StepResult timedOut = execution(id).latestResultFor(StepId.of("publish")).orElseThrow();
        assertEquals(StepStatus.FAILED, timedOut.status());
        assertTrue(timedOut.errorMessage().contains("globalTimeout"));
    }

    // ------------------------------------------------------------------ approbation, pause, arret

    private static ExecutionPolicy approvalOnPublish() {
        return new ExecutionPolicy(Duration.ofMinutes(5), List.of(StepId.of("publish")), 1);
    }

    @Test
    void aManualApprovalStepPausesBeforeRunningAndResumeApprovesIt() {
        ExecutionId id = run(publishArticle(approvalOnPublish()));

        assertEquals(WorkflowExecutionStatus.PAUSED, engine.status(id));
        assertEquals(List.of("POST /internal/validate", "POST /internal/reserve-slot"), invoker.keys());
        assertEquals(List.of(StepStatus.PENDING), statusesOf(id, "publish"));

        engine.resume(id);

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
        assertEquals("POST /internal/publish", invoker.keys().get(2));
        assertEquals(List.of(StepStatus.PENDING, StepStatus.SUCCEEDED), statusesOf(id, "publish"));
    }

    @Test
    void pauseTakesEffectBetweenStepsAndResumeContinuesWithoutRepeatingDoneSteps() {
        invoker.before = call -> {
            if (call.key().equals("POST /internal/validate")) {
                engine.pause(store.allExecutionIds().get(0));
            }
        };
        ExecutionId id = run(publishArticle());

        assertEquals(WorkflowExecutionStatus.PAUSED, engine.status(id));
        assertEquals(List.of("POST /internal/validate"), invoker.keys());
        assertTrue(execution(id).hasSucceeded(StepId.of("validate-content")),
                "l'appel en cours va a son terme et son resultat est journalise");

        invoker.before = call -> { };
        engine.resume(id);

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
        assertEquals(List.of("POST /internal/validate", "POST /internal/reserve-slot", "POST /internal/publish"),
                invoker.keys());
    }

    @Test
    void terminatingAPausedExecutionCompensatesWhatAlreadySucceeded() {
        ExecutionId id = run(publishArticle(approvalOnPublish()));
        assertEquals(WorkflowExecutionStatus.PAUSED, engine.status(id));

        engine.terminate(id, "annule par l'operateur");

        assertEquals(WorkflowExecutionStatus.COMPENSATED, engine.status(id));
        assertEquals("POST /internal/release-slot", invoker.keys().get(2));
        assertFalse(invoker.keys().contains("POST /internal/publish"));
    }

    @Test
    void terminatingWithNothingToUndoFailsTheExecution() {
        ExecutionId id = run(publishArticle(new ExecutionPolicy(Duration.ofMinutes(5),
                List.of(StepId.of("validate-content")), 1)));
        assertEquals(WorkflowExecutionStatus.PAUSED, engine.status(id));

        engine.terminate(id, "inutile");

        assertEquals(WorkflowExecutionStatus.FAILED, engine.status(id));
        assertTrue(invoker.calls.isEmpty());
    }

    @Test
    void terminatingWhileACallIsInFlightStillCompensatesTheEffectThatLandsAfterwards() {
        invoker.before = call -> {
            if (call.key().equals("POST /internal/validate")) {
                engine.terminate(store.allExecutionIds().get(0), "arret pendant l'appel");
            }
        };
        ExecutionId id = run(workflow(
                step("validate", NEWS, "POST /internal/validate", "POST /internal/undo-validate", RetryPolicy.none())));

        assertEquals(WorkflowExecutionStatus.COMPENSATED, engine.status(id));
        assertEquals(List.of("POST /internal/validate", "POST /internal/undo-validate"), invoker.keys());
    }

    @Test
    void terminatingAFinishedExecutionIsANoOp() {
        ExecutionId id = run(publishArticle());
        engine.terminate(id, "trop tard");

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
    }

    @Test
    void pausingAPausedExecutionIsIdempotentAndResumingATerminalOneIsRejected() {
        ExecutionId paused = run(publishArticle(approvalOnPublish()));
        engine.pause(paused);
        assertEquals(WorkflowExecutionStatus.PAUSED, engine.status(paused));

        ExecutionId done = run(publishArticle());
        assertThrows(IllegalExecutionTransitionException.class, () -> engine.resume(done));
    }

    @Test
    void unknownExecutionsAreReportedExplicitly() {
        ExecutionId unknown = ExecutionId.generate();
        assertThrows(ExecutionNotFoundException.class, () -> engine.status(unknown));
        assertThrows(ExecutionNotFoundException.class, () -> engine.pause(unknown));
        assertThrows(ExecutionNotFoundException.class, () -> engine.resume(unknown));
        assertThrows(ExecutionNotFoundException.class, () -> engine.terminate(unknown, "x"));
    }

    // ------------------------------------------------------------------ durabilite / reprise

    private ExecutionId seedInterruptedAfterValidate(WorkflowExecutionStatus status) {
        WorkflowDefinition definition = publishArticle();
        store.saveDefinition(definition);
        ExecutionId id = ExecutionId.generate();
        store.save(WorkflowExecution.rehydrate(definition.id(), id, status,
                List.of(new StepResult(StepId.of("validate-content"), StepStatus.SUCCEEDED, 1, T0,
                        T0.plusSeconds(1), Map.of(), null)),
                T0, Map.of("articleId", "42")));
        return id;
    }

    @Test
    void recoveryResumesWhereTheExecutionStoppedWithItsOriginalInput() {
        ExecutionId id = seedInterruptedAfterValidate(WorkflowExecutionStatus.RUNNING);

        assertEquals(1, engine.recoverInterruptedExecutions());

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
        assertEquals(List.of("POST /internal/reserve-slot", "POST /internal/publish"), invoker.keys(),
                "validate etait deja reussie : jamais rejouee");
        assertEquals(Map.of("articleId", "42"), invoker.calls.get(0).payload());
        assertEquals("{\"articleId\":\"42\"}", messaging.published.get(0).event().data());
    }

    @Test
    void recoveryAlsoRestartsAnInterruptedCompensation() {
        WorkflowDefinition definition = publishArticle();
        store.saveDefinition(definition);
        ExecutionId id = ExecutionId.generate();
        store.save(WorkflowExecution.rehydrate(definition.id(), id, WorkflowExecutionStatus.COMPENSATING,
                List.of(new StepResult(StepId.of("validate-content"), StepStatus.SUCCEEDED, 1, T0,
                                T0.plusSeconds(1), Map.of(), null),
                        new StepResult(StepId.of("reserve-notification-slot"), StepStatus.SUCCEEDED, 1, T0,
                                T0.plusSeconds(2), Map.of(), null),
                        new StepResult(StepId.of("publish"), StepStatus.FAILED, 1, T0, T0.plusSeconds(3),
                                Map.of(), "HTTP 500")),
                T0, Map.of("articleId", "42")));

        engine.recoverInterruptedExecutions();

        assertEquals(WorkflowExecutionStatus.COMPENSATED, engine.status(id));
        assertEquals(List.of("POST /internal/release-slot"), invoker.keys());
    }

    @Test
    void recoveryNeverRestartsAPausedOrFinishedExecution() {
        ExecutionId paused = seedInterruptedAfterValidate(WorkflowExecutionStatus.PAUSED);
        ExecutionId completed = seedInterruptedAfterValidate(WorkflowExecutionStatus.COMPLETED);

        assertEquals(0, engine.recoverInterruptedExecutions());

        assertEquals(WorkflowExecutionStatus.PAUSED, engine.status(paused));
        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(completed));
        assertTrue(invoker.calls.isEmpty());
    }

    @Test
    void anInterruptedRetryWaitLeavesTheExecutionResumable() {
        invoker.respond("POST /internal/validate", status(503), status(200));
        WorkflowEngine interruptible = newEngine(duration -> {
            throw new InterruptedException("arret du Kernel");
        });

        ExecutionId id = interruptible.start(publishArticle(), new WorkflowInput(Map.of("articleId", "42")));
        boolean flagRestored = Thread.interrupted(); // lit ET efface le drapeau pour la suite du test

        assertTrue(flagRestored, "le drapeau d'interruption doit etre restaure");
        assertEquals(WorkflowExecutionStatus.RUNNING, engine.status(id));
        assertEquals(List.of(StepStatus.FAILED), statusesOf(id, "validate-content"));

        assertEquals(1, engine.recoverInterruptedExecutions());

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
        assertEquals(List.of(StepStatus.FAILED, StepStatus.SUCCEEDED), statusesOf(id, "validate-content"));
        assertEquals(List.of(1, 2), execution(id).stepResults().stream()
                .filter(r -> r.stepId().equals(StepId.of("validate-content"))).map(StepResult::attempt).toList());
    }

    @Test
    void aMissingDefinitionFailsTheExecutionVisiblyInsteadOfHangingForever() {
        ExecutionId id = seedInterruptedAfterValidate(WorkflowExecutionStatus.RUNNING);
        store.deleteDefinition(WorkflowId.of("publish-article-workflow"));

        engine.recoverInterruptedExecutions();

        assertEquals(WorkflowExecutionStatus.FAILED, engine.status(id));
        assertTrue(invoker.calls.isEmpty());
    }

    @Test
    void startPersistsTheDefinitionAndTheInputBeforeDriving() {
        ExecutionId id = run(publishArticle());

        assertTrue(store.findDefinition(WorkflowId.of("publish-article-workflow")).isPresent());
        assertEquals(Map.of("articleId", "42"), execution(id).input());
        assertEquals(T0, execution(id).startedAt());
    }

    @Test
    void aWorkflowIsNotDrivenByTwoConcurrentDriversOnTheSameExecution() {
        // Un executor qui ne lance rien : start() persiste sans piloter ; deux
        // resume() successifs planifient deux taches mais un seul pilote agit.
        List<Runnable> queued = new ArrayList<>();
        WorkflowEngine deferred = new WorkflowEngine(store, discovery, invoker, messaging, clock, sleeps::add,
                queued::add, WorkflowEngineSettings.defaults());
        ExecutionId id = deferred.start(publishArticle(), WorkflowInput.empty());
        deferred.resume(id);
        assertEquals(2, queued.size());

        queued.forEach(Runnable::run);

        assertEquals(WorkflowExecutionStatus.COMPLETED, engine.status(id));
        assertEquals(3, invoker.calls.size(), "chaque operation HTTP n'est appelee qu'une fois");
    }
}
