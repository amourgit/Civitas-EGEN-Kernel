package africa.civitas.egen.application.workflow;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.InvocationResult;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.ResolvedInstance;
import africa.civitas.egen.application.port.ResolvedInstances;
import africa.civitas.egen.application.port.ServiceInvocationPort;
import africa.civitas.egen.application.port.WorkflowStorePort;
import africa.civitas.egen.application.resilience.CallClassification;
import africa.civitas.egen.application.resilience.CircuitBreaker;
import africa.civitas.egen.application.resilience.ResiliencyPolicyProvider;
import africa.civitas.egen.application.resilience.ServiceCircuitBreakers;
import africa.civitas.egen.domain.event.TechnicalEvent;
import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.Step;
import africa.civitas.egen.domain.workflow.StepId;
import africa.civitas.egen.domain.workflow.StepOperation;
import africa.civitas.egen.domain.workflow.StepResult;
import africa.civitas.egen.domain.workflow.StepStatus;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowInput;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * Moteur de Workflow — Saga orchestree durable (voir
 * docs/architecture/11-moteur-de-workflow.md, "Moteur d'execution — regles
 * exactes", dont ce code est l'implementation fidele).
 *
 * <p>Principes structurants :</p>
 * <ul>
 *   <li><b>L'etat vit dans le store, jamais en memoire.</b> Chaque iteration
 *       relit l'execution via {@link WorkflowStorePort} : c'est ce qui rend
 *       la reprise apres redemarrage identique a la poursuite normale.</li>
 *   <li><b>Un fait final par tentative.</b> Le store est insert-only avec une
 *       cle {@code (execution, etape, tentative)} : aucune ligne
 *       intermediaire RUNNING n'est ecrite.</li>
 *   <li><b>Pas de verrou pendant un appel reseau.</b> Les verrous (par
 *       execution) ne couvrent que les sequences lecture-modification-
 *       ecriture du store, pour que {@code pause}/{@code terminate}
 *       n'ecrasent jamais un statut ecrit en parallele et inversement.</li>
 *   <li><b>Un seul pilote par execution</b> a la fois dans ce processus.</li>
 * </ul>
 */
public final class WorkflowEngine implements WorkflowEnginePort {

    private static final System.Logger LOG = System.getLogger(WorkflowEngine.class.getName());
    private static final String EVENT_SOURCE_PREFIX = "/egen/workflows/";
    private static final int LOCK_STRIPES = 64;

    private final WorkflowStorePort store;
    private final DiscoveryPort discovery;
    private final ServiceInvocationPort invocation;
    private final MessagingPort messaging;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Executor executor;
    private final WorkflowEngineSettings settings;
    private final ResiliencyPolicyProvider policies;
    private final ServiceCircuitBreakers breakers;

    private final Object[] locks = new Object[LOCK_STRIPES];
    private final Set<String> driving = ConcurrentHashMap.newKeySet();

    /** Constructeur historique : politique de resilience par defaut pour toutes les cibles. */
    public WorkflowEngine(WorkflowStorePort store, DiscoveryPort discovery, ServiceInvocationPort invocation,
                          MessagingPort messaging, Clock clock, Sleeper sleeper, Executor executor,
                          WorkflowEngineSettings settings) {
        this(store, discovery, invocation, messaging, clock, sleeper, executor, settings,
                ResiliencyPolicyProvider.defaultsOnly());
    }

    public WorkflowEngine(WorkflowStorePort store, DiscoveryPort discovery, ServiceInvocationPort invocation,
                          MessagingPort messaging, Clock clock, Sleeper sleeper, Executor executor,
                          WorkflowEngineSettings settings, ResiliencyPolicyProvider policies) {
        this.store = Objects.requireNonNull(store, "store");
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.invocation = Objects.requireNonNull(invocation, "invocation");
        this.messaging = Objects.requireNonNull(messaging, "messaging");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.breakers = new ServiceCircuitBreakers(clock);
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    // ------------------------------------------------------------------
    // WorkflowEnginePort
    // ------------------------------------------------------------------

    @Override
    public ExecutionId start(WorkflowDefinition definition, WorkflowInput input) {
        Objects.requireNonNull(definition, "definition");
        store.saveDefinition(definition);
        ExecutionId id = ExecutionId.generate();
        Map<String, String> values = input == null ? Map.of() : input.values();
        store.save(WorkflowExecution.start(definition.id(), id, clock.instant(), values));
        schedule(id);
        return id;
    }

    @Override
    public void pause(ExecutionId id) {
        synchronized (lockFor(id)) {
            WorkflowExecution current = load(id);
            store.save(current.withStatus(WorkflowExecutionStatus.PAUSED));
        }
    }

    @Override
    public void resume(ExecutionId id) {
        synchronized (lockFor(id)) {
            WorkflowExecution current = load(id);
            boolean alreadyActive = current.status() == WorkflowExecutionStatus.RUNNING
                    || current.status() == WorkflowExecutionStatus.COMPENSATING;
            if (!alreadyActive) {
                // Leve IllegalExecutionTransitionException si deja terminee.
                store.save(current.withStatus(WorkflowExecutionStatus.RUNNING));
            }
        }
        schedule(id);
    }

    @Override
    public void terminate(ExecutionId id, String reason) {
        boolean needsDriving;
        synchronized (lockFor(id)) {
            WorkflowExecution current = load(id);
            if (isTerminal(current.status())) {
                return;
            }
            WorkflowExecution next = current;
            if (current.status() != WorkflowExecutionStatus.COMPENSATING) {
                WorkflowDefinition definition = store.findDefinition(current.workflowId()).orElse(null);
                boolean hasCompensable = definition != null && !compensableSteps(definition, current).isEmpty();
                // Un pilote en cours peut faire aboutir un appel apres coup : il faut alors
                // passer par COMPENSATING pour que cet effet ne reste jamais orphelin.
                boolean compensate = hasCompensable || driving.contains(id.value());
                if (current.status() == WorkflowExecutionStatus.PAUSED) {
                    next = next.withStatus(WorkflowExecutionStatus.RUNNING);
                }
                next = next.withStatus(compensate
                        ? WorkflowExecutionStatus.COMPENSATING : WorkflowExecutionStatus.FAILED);
                store.save(next);
            }
            needsDriving = next.status() == WorkflowExecutionStatus.COMPENSATING;
        }
        LOG.log(Level.INFO, "Execution {0} arretee sur demande : {1}", id, reason);
        if (needsDriving) {
            schedule(id);
        }
    }

    @Override
    public WorkflowExecutionStatus status(ExecutionId id) {
        return load(id).status();
    }

    /**
     * Relance toute execution RUNNING/COMPENSATING connue du store — a
     * appeler une fois au demarrage du Kernel (voir docs/architecture/11,
     * "Reprise au demarrage"). Les executions PAUSED attendent un
     * {@link #resume} explicite.
     *
     * @return le nombre d'executions relancees
     */
    public int recoverInterruptedExecutions() {
        List<ExecutionId> ids = store.findRunningExecutions();
        for (ExecutionId id : ids) {
            schedule(id);
        }
        return ids.size();
    }

    // ------------------------------------------------------------------
    // Pilotage
    // ------------------------------------------------------------------

    private void schedule(ExecutionId id) {
        executor.execute(() -> {
            try {
                drive(id);
            } catch (RuntimeException e) {
                LOG.log(Level.ERROR, "Pilotage de l'execution " + id
                        + " interrompu par une erreur inattendue ; reprise au prochain demarrage ou resume()", e);
            }
        });
    }

    /**
     * Un seul pilote par execution. La re-verification apres liberation
     * ferme la course "le pilote va sortir pendant qu'un resume() arrive et
     * constate qu'un pilote existe encore".
     */
    private void drive(ExecutionId id) {
        while (true) {
            if (!driving.add(id.value())) {
                return;
            }
            boolean finishedCleanly;
            try {
                finishedCleanly = runLoop(id);
            } finally {
                driving.remove(id.value());
            }
            if (!finishedCleanly || !needsDriving(id)) {
                return;
            }
        }
    }

    private boolean needsDriving(ExecutionId id) {
        return store.findById(id)
                .map(e -> e.status() == WorkflowExecutionStatus.RUNNING
                        || e.status() == WorkflowExecutionStatus.COMPENSATING)
                .orElse(false);
    }

    /** @return false si le thread a ete interrompu (l'execution reste reprenable) */
    private boolean runLoop(ExecutionId id) {
        while (true) {
            WorkflowExecution execution = store.findById(id).orElse(null);
            if (execution == null) {
                return true;
            }
            switch (execution.status()) {
                case RUNNING -> {
                    if (!forwardStep(execution)) {
                        return false;
                    }
                }
                case COMPENSATING -> compensationStep(execution);
                default -> {
                    return true; // PAUSED ou terminal : rien a piloter
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Avancement
    // ------------------------------------------------------------------

    /** @return false si interrompu pendant l'attente entre deux tentatives */
    private boolean forwardStep(WorkflowExecution execution) {
        WorkflowDefinition definition = definitionOrAbort(execution);
        if (definition == null) {
            return true;
        }
        Optional<Step> next = nextRunnable(definition, execution);
        if (next.isEmpty()) {
            boolean allSucceeded = definition.steps().stream().allMatch(s -> execution.hasSucceeded(s.id()));
            if (allSucceeded) {
                mutate(execution.executionId(), cur -> cur.status() == WorkflowExecutionStatus.RUNNING
                        ? cur.withStatus(WorkflowExecutionStatus.COMPLETED) : cur);
            } else {
                LOG.log(Level.ERROR, "Execution {0} : aucune etape executable alors que le workflow n'est pas "
                        + "termine — etat incoherent, execution mise en echec", execution.executionId());
                mutate(execution.executionId(), cur -> cur.status() == WorkflowExecutionStatus.RUNNING
                        ? cur.withStatus(failureStatus(definition, cur)) : cur);
            }
            return true;
        }
        Step step = next.get();
        ExecutionId id = execution.executionId();

        if (clock.instant().isAfter(execution.startedAt().plus(definition.executionPolicy().globalTimeout()))) {
            Instant now = clock.instant();
            StepResult timedOut = new StepResult(step.id(), StepStatus.FAILED, nextAttempt(execution, step.id()),
                    now, now, Map.of(),
                    "globalTimeout depasse (" + definition.executionPolicy().globalTimeout() + ")");
            recordFailure(id, definition, timedOut, true);
            return true;
        }

        if (definition.executionPolicy().manualApprovalSteps().contains(step.id())
                && execution.latestResultFor(step.id()).isEmpty()) {
            Instant now = clock.instant();
            mutate(id, cur -> cur.status() == WorkflowExecutionStatus.RUNNING
                    ? cur.withStepResult(new StepResult(step.id(), StepStatus.PENDING,
                            nextAttempt(cur, step.id()), now, null, Map.of(), null))
                            .withStatus(WorkflowExecutionStatus.PAUSED)
                    : cur);
            return true;
        }

        int attempt = nextAttempt(execution, step.id());
        int failuresSoFar = failureCount(execution, step.id());
        Instant started = clock.instant();
        Outcome outcome = perform(execution, step.service(), step.operation(), step.timeout(), false);
        Instant completed = clock.instant();

        if (outcome.success()) {
            StepResult ok = new StepResult(step.id(), StepStatus.SUCCEEDED, attempt, started, completed,
                    outcome.output(), null);
            mutate(id, cur -> cur.withStepResult(new StepResult(ok.stepId(), ok.status(),
                    nextAttempt(cur, step.id()), ok.startedAt(), ok.completedAt(), ok.output(), null)));
            return true;
        }

        StepResult failed = new StepResult(step.id(), StepStatus.FAILED, attempt, started, completed,
                Map.of(), outcome.error());
        boolean definitive = !outcome.retryable() || failuresSoFar + 1 >= step.retry().maxAttempts();
        recordFailure(id, definition, failed, definitive);
        if (definitive) {
            return true;
        }
        try {
            sleeper.sleep(backoff(step, failuresSoFar + 1));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Journalise un echec ; s'il est definitif, change le statut dans la
     * MEME sauvegarde (une seule transaction cote adapter) — jamais un
     * echec journalise sans que son effet sur le statut soit visible.
     */
    private void recordFailure(ExecutionId id, WorkflowDefinition definition, StepResult failed, boolean definitive) {
        mutate(id, cur -> {
            WorkflowExecution updated = cur.withStepResult(new StepResult(failed.stepId(), failed.status(),
                    nextAttempt(cur, failed.stepId()), failed.startedAt(), failed.completedAt(), failed.output(),
                    failed.errorMessage()));
            if (definitive && cur.status() == WorkflowExecutionStatus.RUNNING) {
                updated = updated.withStatus(failureStatus(definition, updated));
            }
            return updated;
        });
    }

    private WorkflowExecutionStatus failureStatus(WorkflowDefinition definition, WorkflowExecution execution) {
        return compensableSteps(definition, execution).isEmpty()
                ? WorkflowExecutionStatus.FAILED : WorkflowExecutionStatus.COMPENSATING;
    }

    private Optional<Step> nextRunnable(WorkflowDefinition definition, WorkflowExecution execution) {
        for (StepId stepId : definition.topologicalOrder()) {
            if (execution.hasSucceeded(stepId)) {
                continue;
            }
            Step step = definition.stepById(stepId).orElseThrow();
            if (step.dependsOn().stream().allMatch(execution::hasSucceeded)) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    private Duration backoff(Step step, int failuresSoFar) {
        Duration base = settings.baseBackoff();
        Duration delay = switch (step.retry().backoff()) {
            case FIXED -> base;
            case EXPONENTIAL -> {
                long factor = 1L << Math.min(failuresSoFar - 1, 20);
                Duration candidate = base.multipliedBy(factor);
                yield candidate.compareTo(settings.maxBackoff()) > 0 ? settings.maxBackoff() : candidate;
            }
        };
        return delay.compareTo(settings.maxBackoff()) > 0 ? settings.maxBackoff() : delay;
    }

    // ------------------------------------------------------------------
    // Compensation (Saga, ordre inverse)
    // ------------------------------------------------------------------

    private void compensationStep(WorkflowExecution execution) {
        WorkflowDefinition definition = definitionOrAbort(execution);
        if (definition == null) {
            return;
        }
        ExecutionId id = execution.executionId();
        List<Step> pending = compensableSteps(definition, execution);
        if (pending.isEmpty()) {
            boolean anyFailed = anyCompensationFailed(execution);
            mutate(id, cur -> cur.status() == WorkflowExecutionStatus.COMPENSATING
                    ? cur.withStatus(anyFailed
                            ? WorkflowExecutionStatus.COMPENSATION_FAILED : WorkflowExecutionStatus.COMPENSATED)
                    : cur);
            return;
        }
        Step step = pending.get(0);
        StepOperation operation = step.compensationOperation().orElseThrow();
        Instant started = clock.instant();
        Outcome outcome = perform(execution, step.service(), operation, step.timeout(), true);
        Instant completed = clock.instant();
        mutate(id, cur -> cur.withStepResult(outcome.success()
                ? new StepResult(step.id(), StepStatus.COMPENSATED, nextAttempt(cur, step.id()), started,
                        completed, Map.of(), null)
                : new StepResult(step.id(), StepStatus.COMPENSATION_FAILED, nextAttempt(cur, step.id()), started,
                        completed, Map.of(), outcome.error())));
    }

    /**
     * Etapes reussies, declarant une compensation, et pas encore compensees
     * (leur dernier resultat est toujours SUCCEEDED), en ordre INVERSE
     * d'execution — la prochaine a compenser est la premiere de la liste.
     */
    private List<Step> compensableSteps(WorkflowDefinition definition, WorkflowExecution execution) {
        List<StepId> succeeded = new ArrayList<>(execution.succeededStepsInExecutionOrder());
        Collections.reverse(succeeded);
        List<Step> compensable = new ArrayList<>();
        for (StepId stepId : succeeded) {
            Step step = definition.stepById(stepId).orElse(null);
            boolean stillSucceeded = execution.latestResultFor(stepId)
                    .map(r -> r.status() == StepStatus.SUCCEEDED).orElse(false);
            if (step != null && step.compensationOperation().isPresent() && stillSucceeded) {
                compensable.add(step);
            }
        }
        return compensable;
    }

    private boolean anyCompensationFailed(WorkflowExecution execution) {
        Set<StepId> seen = new HashSet<>();
        for (StepResult result : execution.stepResults()) {
            seen.add(result.stepId());
        }
        return seen.stream().anyMatch(stepId -> execution.latestResultFor(stepId)
                .map(r -> r.status() == StepStatus.COMPENSATION_FAILED).orElse(false));
    }

    // ------------------------------------------------------------------
    // Execution d'une operation (une tentative)
    // ------------------------------------------------------------------

    private Outcome perform(WorkflowExecution execution, ServiceId service, StepOperation operation,
                            Duration timeout, boolean compensation) {
        try {
            return switch (operation) {
                case StepOperation.Http http -> performHttp(execution, service, http, timeout, compensation);
                case StepOperation.Event event -> performEvent(execution, event);
            };
        } catch (RuntimeException e) {
            return Outcome.failure(e.getClass().getSimpleName() + " : " + e.getMessage(), true);
        }
    }

    /**
     * Resilience de la cible (docs/architecture/12, "Politique appliquee par le
     * Moteur de Workflow") : son circuit breaker et son timeout — pas son retry,
     * deja porte par l'etape. Une compensation n'est jamais rejetee par un
     * circuit ouvert (c'est le mecanisme de securite) mais alimente le circuit.
     */
    private Outcome performHttp(WorkflowExecution execution, ServiceId service, StepOperation.Http http,
                                Duration stepTimeout, boolean compensation) {
        ResolvedInstances resolved = discovery.resolve(service);
        if (resolved.instances().isEmpty()) {
            // Aucun appel n'a lieu : rien a enregistrer dans le circuit (et surtout pas
            // d'essai semi-ouvert consomme sans issue).
            return Outcome.failure("Aucune instance disponible pour le service " + service, true);
        }
        ResiliencyPolicy policy = policies.policyFor(service);
        CircuitBreaker breaker = breakers.forService(service, policy.circuitBreaker());
        if (!compensation && !breaker.tryAcquire()) {
            return Outcome.failure("Circuit ouvert pour le service " + service
                    + " : appel rejete sans acces reseau", true);
        }
        Duration timeout = policy.timeout().compareTo(stepTimeout) < 0 ? policy.timeout() : stepTimeout;
        boolean recorded = false;
        try {
            ResolvedInstance instance = resolved.instances().get(0);
            URI endpoint = URI.create("http://" + instance.address() + ":" + instance.port() + http.path());
            boolean hasBody = !http.method().equals("GET") && !http.method().equals("DELETE");
            InvocationResult result = invocation.invoke(endpoint, http.method(),
                    hasBody ? execution.input() : Map.of(), timeout);
            int status = result.statusCode();
            boolean retryable = CallClassification.isRetryableFailure(status);
            if (retryable) {
                breaker.recordFailure();
            } else {
                breaker.recordSuccess(); // succes, ou reponse definitive 4xx : le service a repondu
            }
            recorded = true;
            if (result.isSuccess()) {
                return Outcome.success(result.body());
            }
            return Outcome.failure("HTTP " + status + " depuis " + service + " (" + http.raw() + ")", retryable);
        } finally {
            if (!recorded) {
                breaker.recordFailure(); // erreur reseau, delai depasse, exception inattendue
            }
        }
    }

    private Outcome performEvent(WorkflowExecution execution, StepOperation.Event event) {
        TechnicalEvent technicalEvent = TechnicalEvent.create(
                EVENT_SOURCE_PREFIX + execution.workflowId().value(), event.eventType(),
                execution.executionId().value(), JsonObjects.toJson(execution.input()));
        messaging.publish(technicalEvent, event.eventType());
        return Outcome.success(Map.of());
    }

    private record Outcome(boolean success, Map<String, String> output, String error, boolean retryable) {
        static Outcome success(Map<String, String> output) {
            return new Outcome(true, output, null, false);
        }

        static Outcome failure(String error, boolean retryable) {
            return new Outcome(false, Map.of(), error, retryable);
        }
    }

    // ------------------------------------------------------------------
    // Journal : numerotation des tentatives et acces au store
    // ------------------------------------------------------------------

    /** max(tentative deja journalisee pour l'etape) + 1 — voir docs/architecture/11. */
    private static int nextAttempt(WorkflowExecution execution, StepId stepId) {
        return execution.stepResults().stream()
                .filter(r -> r.stepId().equals(stepId))
                .mapToInt(StepResult::attempt)
                .max().orElse(0) + 1;
    }

    private static int failureCount(WorkflowExecution execution, StepId stepId) {
        return (int) execution.stepResults().stream()
                .filter(r -> r.stepId().equals(stepId) && r.status() == StepStatus.FAILED)
                .count();
    }

    private static boolean isTerminal(WorkflowExecutionStatus status) {
        return status == WorkflowExecutionStatus.COMPLETED || status == WorkflowExecutionStatus.FAILED
                || status == WorkflowExecutionStatus.COMPENSATED
                || status == WorkflowExecutionStatus.COMPENSATION_FAILED;
    }

    private WorkflowExecution load(ExecutionId id) {
        return store.findById(id).orElseThrow(() -> new ExecutionNotFoundException(id));
    }

    /**
     * Lecture-modification-ecriture atomique vis-a-vis de pause/resume/
     * terminate : relit l'etat courant (qui a pu changer pendant un appel
     * reseau) puis applique la modification dessus — jamais sur une copie
     * perimee.
     */
    private WorkflowExecution mutate(ExecutionId id, UnaryOperator<WorkflowExecution> change) {
        synchronized (lockFor(id)) {
            return store.save(change.apply(load(id)));
        }
    }

    private Object lockFor(ExecutionId id) {
        return locks[Math.floorMod(id.value().hashCode(), LOCK_STRIPES)];
    }

    /**
     * Definition introuvable (supprimee du store) : impossible de continuer
     * ni de compenser — l'execution est marquee en echec visible plutot que
     * de rester RUNNING pour toujours.
     */
    private WorkflowDefinition definitionOrAbort(WorkflowExecution execution) {
        Optional<WorkflowDefinition> definition = store.findDefinition(execution.workflowId());
        if (definition.isPresent()) {
            return definition.get();
        }
        LOG.log(Level.ERROR, "Execution {0} : definition {1} introuvable — execution mise en echec",
                execution.executionId(), execution.workflowId());
        mutate(execution.executionId(), cur -> switch (cur.status()) {
            case RUNNING -> cur.withStatus(WorkflowExecutionStatus.FAILED);
            case COMPENSATING -> cur.withStatus(WorkflowExecutionStatus.COMPENSATION_FAILED);
            default -> cur;
        });
        return null;
    }
}
