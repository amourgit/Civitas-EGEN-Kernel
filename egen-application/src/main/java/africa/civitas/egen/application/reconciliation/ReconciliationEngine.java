package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.port.ServiceInstanceRegistration;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ConditionStatus;
import africa.civitas.egen.domain.lifecycle.LifecycleStateMachine;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.Dependency;
import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.DiscoveryObservation;
import africa.civitas.egen.domain.model.ServiceId;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Le moteur de reconciliation — coeur battant du Kernel (voir
 * docs/architecture/04-moteur-de-reconciliation.md). Depuis la Phase 3,
 * verifie egalement que les dependances REQUIRED sont RUNNING avant de
 * quitter CONFIGURED (voir docs/architecture/10-gestion-des-dependances.md)
 * — Configuration, Secrets et Observability rejoignent la boucle aux
 * phases qui les introduisent (voir docs/architecture/19-feuille-de-route.md).
 *
 * <p>Un seul thread worker consommant la {@link WorkQueue}, resync periodique
 * inconditionnel, retry avec compteur d'echecs consecutifs plafonne. La
 * concurrence bornee multi-worker par type de ressource reste un
 * raffinement de phase ulterieure.</p>
 */
public final class ReconciliationEngine implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(ReconciliationEngine.class.getName());

    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final Duration POLL_DELAY_WHILE_CONVERGING = Duration.ofSeconds(3);
    private static final Duration STOPPING_POLL_DELAY = Duration.ofSeconds(1);
    private static final Duration RESYNC_INTERVAL = Duration.ofMinutes(5);

    private final WorkQueue workQueue;
    private final RegistryStorePort registryStorePort;
    private final DeploymentPort deploymentPort;
    private final DiscoveryPort discoveryPort;
    private final LifecycleStateMachine stateMachine = new LifecycleStateMachine();
    private final ConcurrentHashMap<ServiceId, AtomicInteger> consecutiveFailures =
            new ConcurrentHashMap<>();
    // Marque l'instant ou le deregistrement Discovery a eu lieu pour un arret
    // gracieux en cours (voir handleStopping) — bookkeeping transitoire de
    // l'orchestration, jamais persiste comme etat desire/observe.
    private final ConcurrentHashMap<ServiceId, Instant> stoppingDeregisteredAt =
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
                                 DeploymentPort deploymentPort, DiscoveryPort discoveryPort) {
        this.workQueue = workQueue;
        this.registryStorePort = registryStorePort;
        this.deploymentPort = deploymentPort;
        this.discoveryPort = discoveryPort;
        this.worker = new Thread(this::runLoop, "egen-reconcile-worker");
        this.worker.setDaemon(true);
    }

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
                LOG.log(Level.ERROR, "Reconciliation inattendue en echec", unexpected);
            }
        }
    }

    /**
     * Un passage de reconciliation complet pour {@code id} : Resolve (relit
     * l'etat desire), Compose+Delegate (appelle les ports de facon
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

        // Etats stables en v0, jamais de retry infini silencieux (garde-fou
        // n10, docs/architecture/02-principes-fondamentaux.md).
        if (current.phase() == Phase.FAILED || current.phase() == Phase.STOPPED
                || current.phase() == Phase.REMOVED) {
            return;
        }

        try {
            switch (current.phase()) {
                case DECLARED -> advanceTo(id, current, Phase.REGISTERED, desired.generation());
                case REGISTERED -> advanceTo(id, current, Phase.CONFIGURED, desired.generation());
                case CONFIGURED -> handleConfigured(id, current, desired);
                case DEPLOYING, RUNNING, DEGRADED -> observeAndConverge(id, current, desired);
                case STOPPING -> handleStopping(id, current, desired);
                default -> LOG.log(Level.WARNING,
                        "Phase non pilotee automatiquement par la reconciliation v0 : {0}", current.phase());
            }
            consecutiveFailures.remove(id);
        } catch (RuntimeException adapterFailure) {
            handleAdapterFailure(id, current, desired, adapterFailure);
        }
    }

    /**
     * Verifie que les dependances REQUIRED sont RUNNING avant de deleguer le
     * deploiement (voir docs/architecture/10-gestion-des-dependances.md,
     * §10.3) : une dependance required manquante bloque l'ordonnancement
     * (le service reste en CONFIGURED, reessaie plus tard — la convergence
     * de chaque service independamment, cycle apres cycle, produit
     * naturellement le bon ordre global, sans planificateur centralise) ;
     * une dependance optionnelle manquante ne bloque jamais
     * (degradation gracieuse), seule la Condition le signale.
     */
    private void handleConfigured(ServiceId id, ServiceStatus current, DesiredState desired) {
        List<Dependency> unsatisfiedRequired = new ArrayList<>();
        List<Dependency> unsatisfiedOptional = new ArrayList<>();
        for (Dependency dependency : desired.manifest().dependencies()) {
            boolean running = registryStorePort.findStatus(dependency.serviceId())
                    .map(status -> status.phase() == Phase.RUNNING)
                    .orElse(false);
            if (!running) {
                (dependency.required() ? unsatisfiedRequired : unsatisfiedOptional).add(dependency);
            }
        }

        if (!unsatisfiedRequired.isEmpty()) {
            Condition condition = new Condition("DependenciesSatisfied", ConditionStatus.FALSE,
                    "RequiredDependencyNotRunning", "en attente de : " + names(unsatisfiedRequired),
                    Instant.now());
            saveStatus(id, Phase.CONFIGURED, mergeConditions(current.conditions(), condition),
                    desired.generation());
            workQueue.requeueAfter(id, POLL_DELAY_WHILE_CONVERGING);
            return;
        }

        Condition dependenciesCondition = unsatisfiedOptional.isEmpty()
                ? new Condition("DependenciesSatisfied", ConditionStatus.TRUE, "AllSatisfied",
                        "Toutes les dependances declarees sont RUNNING", Instant.now())
                : new Condition("DependenciesSatisfied", ConditionStatus.FALSE,
                        "OptionalDependencyUnavailable",
                        "en attente (facultatif, ne bloque pas) de : " + names(unsatisfiedOptional),
                        Instant.now());

        deploymentPort.create(id, desired.manifest().deployment());
        advanceTo(id, current, Phase.DEPLOYING, desired.generation(),
                mergeConditions(current.conditions(), dependenciesCondition));
        workQueue.requeueAfter(id, POLL_DELAY_WHILE_CONVERGING);
    }

    private static String names(List<Dependency> dependencies) {
        return dependencies.stream().map(d -> d.serviceId().value()).collect(Collectors.joining(", "));
    }

    private void observeAndConverge(ServiceId id, ServiceStatus current, DesiredState desired) {
        // Level-triggered : on re-delegue (idempotent) avant d'observer, pour
        // corriger tout ecart meme si un cycle precedent a ete interrompu.
        deploymentPort.create(id, desired.manifest().deployment());
        DeploymentObservation deploymentObservation = deploymentPort.getStatus(id);

        // Enregistrement Discovery de chaque allocation saine et joignable
        // (idempotent, voir DiscoveryPort). Le nettoyage fin d'une allocation
        // qui disparait EN COURS de vie (rescheduling Nomad) est laisse a
        // l'expiration du health check Consul en V2 — seul l'arret explicite
        // (STOPPING, voir handleStopping) deregistre precisement (voir 09.3).
        for (AllocationInfo allocation : deploymentPort.listAllocations(id)) {
            if ("running".equals(allocation.status()) && allocation.hasNetworkInfo()) {
                discoveryPort.register(new ServiceInstanceRegistration(id, allocation.allocationId(),
                        allocation.address(), allocation.port(), desired.manifest().health()));
            }
        }
        DiscoveryObservation discoveryObservation =
                new DiscoveryObservation(discoveryPort.resolve(id).instances().size());

        boolean deploymentConverged = deploymentObservation.desiredCount() > 0
                && deploymentObservation.isConverged();
        boolean discoveryConverged = discoveryObservation.healthyInstanceCount()
                >= deploymentObservation.desiredCount();
        boolean converged = deploymentConverged && discoveryConverged;

        Phase nextPhase;
        if (converged) {
            nextPhase = Phase.RUNNING;
        } else if (current.phase() == Phase.RUNNING || current.phase() == Phase.DEGRADED) {
            // Convergence perdue depuis un etat nominal : degradation, pas un echec.
            nextPhase = Phase.DEGRADED;
        } else {
            nextPhase = Phase.DEPLOYING; // premiere convergence pas encore atteinte
        }

        List<Condition> updates = List.of(
                new Condition("DeploymentReady",
                        deploymentConverged ? ConditionStatus.TRUE : ConditionStatus.FALSE,
                        deploymentConverged ? "Converged" : "AwaitingConvergence",
                        "desired=%d healthy=%d running=%d failed=%d".formatted(
                                deploymentObservation.desiredCount(), deploymentObservation.healthyCount(),
                                deploymentObservation.runningCount(), deploymentObservation.failedCount()),
                        Instant.now()),
                new Condition("DiscoveryReady",
                        discoveryConverged ? ConditionStatus.TRUE : ConditionStatus.FALSE,
                        discoveryConverged ? "Converged" : "AwaitingConvergence",
                        "healthyInstances=%d desired=%d".formatted(
                                discoveryObservation.healthyInstanceCount(), deploymentObservation.desiredCount()),
                        Instant.now()));
        List<Condition> conditions = mergeConditions(current.conditions(), updates);

        if (current.phase() == nextPhase) {
            saveStatus(id, nextPhase, conditions, desired.generation());
        } else {
            advanceTo(id, current, nextPhase, desired.generation(), conditions);
        }

        Duration nextDelay = converged ? RESYNC_INTERVAL : POLL_DELAY_WHILE_CONVERGING;
        workQueue.requeueAfter(id, nextDelay);
    }

    /**
     * Ordre d'arret gracieux imperatif (voir docs/architecture/09-cycle-de-vie.md,
     * §9.3) : d'abord deregistrer (Discovery), attendre la fenetre de
     * propagation (drainConnections), ensuite seulement arreter le
     * deploiement. Inverser cet ordre est l'erreur la plus frequente en
     * production sur ce genre de plateforme.
     */
    private void handleStopping(ServiceId id, ServiceStatus current, DesiredState desired) {
        Instant deregisteredAt = stoppingDeregisteredAt.get(id);
        Duration gracePeriod = desired.manifest().lifecycle().shutdownGracePeriod();

        if (deregisteredAt == null) {
            for (AllocationInfo allocation : deploymentPort.listAllocations(id)) {
                discoveryPort.deregister(id, allocation.allocationId());
            }
            stoppingDeregisteredAt.put(id, Instant.now());
            workQueue.requeueAfter(id, gracePeriod);
            return;
        }

        if (Instant.now().isBefore(deregisteredAt.plus(gracePeriod))) {
            workQueue.requeueAfter(id, STOPPING_POLL_DELAY);
            return;
        }

        deploymentPort.stop(id);
        stoppingDeregisteredAt.remove(id);
        advanceTo(id, current, Phase.STOPPED, desired.generation());
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

    /**
     * Fusionne des Conditions par {@code type} (modele Kubernetes
     * Conditions, voir docs/architecture/09-cycle-de-vie.md, §9.2) : chaque
     * type de Condition (DependenciesSatisfied, DeploymentReady,
     * DiscoveryReady...) evolue independamment des autres au fil des
     * phases — une observation de deploiement ne doit jamais effacer la
     * derniere Condition de dependances connue, et inversement.
     */
    private static List<Condition> mergeConditions(List<Condition> existing, Condition update) {
        return mergeConditions(existing, List.of(update));
    }

    private static List<Condition> mergeConditions(List<Condition> existing, List<Condition> updates) {
        Map<String, Condition> byType = new LinkedHashMap<>();
        for (Condition condition : existing) {
            byType.put(condition.type(), condition);
        }
        for (Condition condition : updates) {
            byType.put(condition.type(), condition);
        }
        return List.copyOf(byType.values());
    }

    private void handleAdapterFailure(ServiceId id, ServiceStatus current, DesiredState desired,
                                       RuntimeException failure) {
        int failures = consecutiveFailures
                .computeIfAbsent(id, ignored -> new AtomicInteger(0))
                .incrementAndGet();
        LOG.log(Level.WARNING, "Echec d'adapter pour {0} ({1}/{2}) : {3}",
                id, failures, MAX_CONSECUTIVE_FAILURES, failure.getMessage());

        if (failures >= MAX_CONSECUTIVE_FAILURES
                && stateMachine.isTransitionAllowed(current.phase(), Phase.FAILED)) {
            Condition failedCondition = new Condition("DeploymentReady", ConditionStatus.FALSE,
                    "AdapterFailureThresholdExceeded", failure.getMessage(), Instant.now());
            advanceTo(id, current, Phase.FAILED, desired.generation(),
                    mergeConditions(current.conditions(), failedCondition));
            consecutiveFailures.remove(id);
            return;
        }
        long backoffSeconds = Math.min(60, (long) Math.pow(2, failures));
        workQueue.requeueAfter(id, Duration.ofSeconds(backoffSeconds));
    }
}
