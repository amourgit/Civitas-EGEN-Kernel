package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.DeploymentException;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ConditionStatus;
import africa.civitas.egen.domain.lifecycle.LifecycleStateMachine;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Le moteur de reconciliation — coeur battant du Kernel (voir
 * docs/architecture/04-moteur-de-reconciliation.md). v0 : un seul thread
 * worker consommant la {@link WorkQueue}, resync periodique inconditionnel,
 * retry avec compteur d'echecs consecutifs plafonne (voir
 * docs/architecture/19-feuille-de-route.md, Phase 1). La concurrence bornee
 * multi-worker par type de ressource est un raffinement de phase
 * ulterieure.
 *
 * <p>Chaque appel de {@link #reconcile(ServiceId)} relit l'etat desire ET
 * l'etat observe dans leur integralite avant d'agir — jamais edge-triggered
 * (voir 04.1). Ne connait qu'un seul port secondaire pour l'instant :
 * {@link DeploymentPort} — Discovery, Messaging, Configuration, Secrets et
 * Observability rejoignent la boucle aux phases qui les introduisent.</p>
 */
public final class ReconciliationEngine implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(ReconciliationEngine.class.getName());

    /** Compteur d'echecs consecutifs au-dela duquel un service passe en FAILED. */
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private static final Duration POLL_DELAY_WHILE_CONVERGING = Duration.ofSeconds(3);
    private static final Duration RESYNC_INTERVAL = Duration.ofMinutes(5);

    private final WorkQueue workQueue;
    private final RegistryStorePort registryStorePort;
    private final DeploymentPort deploymentPort;
    private final LifecycleStateMachine stateMachine = new LifecycleStateMachine();
    private final ConcurrentHashMap<ServiceId, AtomicInteger> consecutiveFailures =
            new ConcurrentHashMap<>();

    private final Thread worker;
    private final ScheduledExecutorService resyncScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "egen-reconcile-resync");
                t.setDaemon(true);
                return t;
            });
    private volatile boolean running = false;

    public ReconciliationEngine(WorkQueue workQueue, RegistryStorePort registryStorePort,
                                 DeploymentPort deploymentPort) {
        this.workQueue = workQueue;
        this.registryStorePort = registryStorePort;
        this.deploymentPort = deploymentPort;
        this.worker = new Thread(this::runLoop, "egen-reconcile-worker");
        this.worker.setDaemon(true);
    }

    /** Demarre le worker et le resync periodique inconditionnel. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        worker.start();
        resyncScheduler.scheduleAtFixedRate(this::triggerResyncOfAllKnownServices,
                RESYNC_INTERVAL.toSeconds(), RESYNC_INTERVAL.toSeconds(), TimeUnit.SECONDS);
        LOG.log(Level.INFO, "ReconciliationEngine demarre (resync toutes les {0})", RESYNC_INTERVAL);
    }

    @Override
    public void close() {
        running = false;
        workQueue.shutdown();
        resyncScheduler.shutdownNow();
        worker.interrupt();
    }

    private void triggerResyncOfAllKnownServices() {
        List<ServiceId> all = registryStorePort.findAllIds();
        LOG.log(Level.DEBUG, "Resync periodique : {0} service(s) connu(s)", all.size());
        workQueue.enqueueAll(all);
    }

    private void runLoop() {
        while (running) {
            try {
                ServiceId id = workQueue.take();
                reconcile(id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException unexpected) {
                // Une reconciliation ne doit jamais tuer le worker : elle est
                // reessayee au prochain resync/enqueue (voir 04.2, retry).
                LOG.log(Level.ERROR, "Reconciliation inattendue en echec", unexpected);
            }
        }
    }

    /**
     * Un passage de reconciliation complet pour {@code id} : Resolve (relit
     * l'etat desire), Compose+Delegate (appelle le DeploymentPort de facon
     * idempotente), Observe (relit l'etat reel), Reconcile (met a jour le
     * statut et decide de la suite).
     */
    void reconcile(ServiceId id) {
        Optional<DesiredState> desiredOpt = registryStorePort.findById(id);
        if (desiredOpt.isEmpty()) {
            LOG.log(Level.WARNING, "Reconciliation demandee pour un service inconnu : {0}", id);
            return;
        }
        DesiredState desired = desiredOpt.get();
        ServiceStatus current = registryStorePort.findStatus(id).orElse(ServiceStatus.initial());

        // FAILED/STOPPED : etats stables en v0, jamais de retry infini silencieux
        // (garde-fou n10, docs/architecture/02-principes-fondamentaux.md). Une
        // action corrective explicite (nouvelle generation, commande impérative)
        // est necessaire pour en sortir.
        if (current.phase() == Phase.FAILED || current.phase() == Phase.STOPPED) {
            return;
        }

        try {
            switch (current.phase()) {
                case DECLARED -> advanceTo(id, current, Phase.REGISTERED, desired.generation());
                case REGISTERED -> {
                    deploymentPort.create(id, desired.manifest().deployment());
                    advanceTo(id, current, Phase.DEPLOYING, desired.generation());
                    workQueue.requeueAfter(id, POLL_DELAY_WHILE_CONVERGING);
                }
                case DEPLOYING -> observeAndConverge(id, current, desired);
                case RUNNING -> observeAndConverge(id, current, desired);
                default -> LOG.log(Level.WARNING, "Phase non geree par la reconciliation v0 : {0}", current.phase());
            }
            consecutiveFailures.remove(id);
        } catch (DeploymentException adapterFailure) {
            handleAdapterFailure(id, current, desired, adapterFailure);
        }
    }

    private void observeAndConverge(ServiceId id, ServiceStatus current, DesiredState desired) {
        // Level-triggered : on re-delegue (idempotent) avant d'observer, pour
        // corriger tout ecart meme si un cycle precedent a ete interrompu
        // (crash du Kernel en plein Delegate, webhook manque...).
        deploymentPort.create(id, desired.manifest().deployment());
        DeploymentObservation observation = deploymentPort.getStatus(id);

        boolean converged = observation.desiredCount() > 0 && observation.isConverged();
        Phase nextPhase = converged ? Phase.RUNNING : Phase.DEPLOYING;

        Condition deploymentReady = new Condition(
                "DeploymentReady",
                converged ? ConditionStatus.TRUE : ConditionStatus.FALSE,
                converged ? "Converged" : "AwaitingConvergence",
                "desired=%d healthy=%d running=%d failed=%d".formatted(
                        observation.desiredCount(), observation.healthyCount(),
                        observation.runningCount(), observation.failedCount()),
                Instant.now());

        if (current.phase() == nextPhase) {
            saveStatus(id, nextPhase, List.of(deploymentReady), desired.generation());
        } else {
            advanceTo(id, current, nextPhase, desired.generation(), List.of(deploymentReady));
        }

        Duration nextDelay = converged ? RESYNC_INTERVAL : POLL_DELAY_WHILE_CONVERGING;
        workQueue.requeueAfter(id, nextDelay);
    }

    private void advanceTo(ServiceId id, ServiceStatus current, Phase next, long generation) {
        advanceTo(id, current, next, generation, current.conditions());
    }

    private void advanceTo(ServiceId id, ServiceStatus current, Phase next, long generation,
                            List<Condition> conditions) {
        stateMachine.assertTransitionAllowed(current.phase(), next);
        saveStatus(id, next, conditions, generation);
        workQueue.enqueue(id); // continue immediatement l'avancement de phase
    }

    private void saveStatus(ServiceId id, Phase phase, List<Condition> conditions, long generation) {
        registryStorePort.saveStatus(id, new ServiceStatus(phase, conditions, generation));
    }

    private void handleAdapterFailure(ServiceId id, ServiceStatus current, DesiredState desired,
                                       DeploymentException failure) {
        int failures = consecutiveFailures
                .computeIfAbsent(id, ignored -> new AtomicInteger(0))
                .incrementAndGet();
        LOG.log(Level.WARNING, "Echec d'adapter pour {0} ({1}/{2}) : {3}",
                id, failures, MAX_CONSECUTIVE_FAILURES, failure.getMessage());

        if (failures >= MAX_CONSECUTIVE_FAILURES
                && stateMachine.isTransitionAllowed(current.phase(), Phase.FAILED)) {
            Condition failedCondition = new Condition("DeploymentReady", ConditionStatus.FALSE,
                    "AdapterFailureThresholdExceeded", failure.getMessage(), Instant.now());
            advanceTo(id, current, Phase.FAILED, desired.generation(), List.of(failedCondition));
            consecutiveFailures.remove(id);
            return;
        }
        // Backoff exponentiel plafonne (2^n secondes, max 60s) avant de reessayer.
        long backoffSeconds = Math.min(60, (long) Math.pow(2, failures));
        workQueue.requeueAfter(id, Duration.ofSeconds(backoffSeconds));
    }
}
