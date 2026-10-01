package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.MetricSample;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.ReconciliationEvent;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.port.ServiceInstanceRegistration;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.port.TraceSpan;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Le moteur de reconciliation — coeur battant du Kernel (voir
 * docs/architecture/04-moteur-de-reconciliation.md). Depuis la Phase 4,
 * chaque cycle genere un span racine "reconcile(serviceId)" avec des spans
 * enfants par appel de port, et alimente les metriques et logs structures
 * documentes en docs/architecture/15-observabilite.md — Configuration et
 * Secrets ne sont pas encore consommes par la boucle elle-meme (ils
 * rejoindront la reconciliation quand un besoin reel de resolution
 * dynamique se presentera, jamais par anticipation).
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
    private final ObservabilityPort observabilityPort;
    private final LifecycleStateMachine stateMachine = new LifecycleStateMachine();
    private final ConcurrentHashMap<ServiceId, AtomicInteger> consecutiveFailures =
            new ConcurrentHashMap<>();
    // Marque l'instant ou le deregistrement Discovery a eu lieu pour un arret
    // gracieux en cours (voir handleStopping) — bookkeeping transitoire de
    // l'orchestration, jamais persiste comme etat desire/observe.
    private final ConcurrentHashMap<ServiceId, Instant> stoppingDeregisteredAt =
            new ConcurrentHashMap<>();

    private final PendingDeclareTraces pendingDeclareTraces;

    private final Thread worker;
    private final ScheduledExecutorService resyncScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "egen-reconcile-resync");
                t.setDaemon(true);
                return t;
            });
    private volatile boolean running = false;

    /**
     * Conserve pour compatibilite (tests qui ne verifient pas le lien de
     * trace declare/reconciliation) : cree son propre
     * {@link PendingDeclareTraces} isole, jamais partage avec un use case —
     * le lien de trace ne fonctionne simplement pas pour cette instance.
     */
    public ReconciliationEngine(WorkQueue workQueue, RegistryStorePort registryStorePort,
                                 DeploymentPort deploymentPort, DiscoveryPort discoveryPort,
                                 ObservabilityPort observabilityPort) {
        this(workQueue, registryStorePort, deploymentPort, discoveryPort, observabilityPort,
                new PendingDeclareTraces());
    }

    public ReconciliationEngine(WorkQueue workQueue, RegistryStorePort registryStorePort,
                                 DeploymentPort deploymentPort, DiscoveryPort discoveryPort,
                                 ObservabilityPort observabilityPort,
                                 PendingDeclareTraces pendingDeclareTraces) {
        this.workQueue = workQueue;
        this.registryStorePort = registryStorePort;
        this.deploymentPort = deploymentPort;
        this.discoveryPort = discoveryPort;
        this.observabilityPort = observabilityPort;
        this.pendingDeclareTraces = pendingDeclareTraces;
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
     * Un passage de reconciliation complet pour {@code id}, instrumente
     * d'un span racine (voir docs/architecture/15-observabilite.md,
     * "chaque cycle de reconciliation genere un span racine
     * reconcile(serviceId)") et d'un {@code operationId} correle aux logs
     * (voir docs/architecture/04-moteur-de-reconciliation.md, §4.3).
     *
     * <p>Le span racine n'est PAS systematiquement independant : si un
     * appel Declare ou Stop de l'API de controle a enregistre un contexte
     * de trace en attente pour cette generation precise (voir
     * {@link PendingDeclareTraces}), ce cycle — et ceux qui suivront tant
     * que le premier appel reseau reel n'aura pas eu lieu — devient un
     * enfant de ce contexte, realisant la propagation de bout en bout
     * demandee par docs/architecture/15-observabilite.md. Sans contexte en
     * attente (resync periodique, convergence en cours, generation
     * perimee), le span racine reste independant comme avant.</p>
     */
    void reconcile(ServiceId id) {
        Instant startedAt = Instant.now();
        Optional<DesiredState> desiredOpt = registryStorePort.findById(id);
        TraceContext triggeringParent = desiredOpt
                .flatMap(desired -> pendingDeclareTraces.peek(id, desired.generation()))
                .orElse(null);

        TraceSpan rootSpan = observabilityPort.startSpan("reconcile", triggeringParent);
        rootSpan.setAttribute("service.id", id.value());
        String operationId = UUID.randomUUID().toString();
        rootSpan.setAttribute("operation.id", operationId);
        try {
            reconcileInternal(id, desiredOpt, operationId, rootSpan);
        } finally {
            rootSpan.close();
            double seconds = Duration.between(startedAt, Instant.now()).toNanos() / 1_000_000_000.0;
            observabilityPort.recordDuration(new MetricSample("egen_reconcile_duration_seconds", seconds,
                    Map.of("service", id.value())));
        }
    }

    private void reconcileInternal(ServiceId id, Optional<DesiredState> desiredOpt, String operationId,
                                    TraceSpan rootSpan) {
        if (desiredOpt.isEmpty()) {
            LOG.log(Level.WARNING, "Reconciliation demandee pour un service inconnu : {0}", id);
            return;
        }
        DesiredState desired = desiredOpt.get();
        ServiceStatus current = registryStorePort.findStatus(id).orElse(ServiceStatus.initial());
        observabilityPort.recordEvent(new ReconciliationEvent(operationId, id, desired.generation(),
                "reconcile phase=" + current.phase(), Instant.now()));

        // Etats stables en v0, jamais de retry infini silencieux (garde-fou
        // n10, docs/architecture/02-principes-fondamentaux.md).
        if (current.phase() == Phase.FAILED || current.phase() == Phase.STOPPED
                || current.phase() == Phase.REMOVED) {
            return;
        }

        TraceContext parent = rootSpan.context();
        try {
            switch (current.phase()) {
                case DECLARED -> advanceTo(id, current, Phase.REGISTERED, desired.generation());
                case REGISTERED -> advanceTo(id, current, Phase.CONFIGURED, desired.generation());
                case CONFIGURED -> handleConfigured(id, current, desired, parent);
                case DEPLOYING, RUNNING, DEGRADED -> observeAndConverge(id, current, desired, parent);
                case STOPPING -> handleStopping(id, current, desired, parent);
                default -> LOG.log(Level.WARNING,
                        "Phase non pilotee automatiquement par la reconciliation v0 : {0}", current.phase());
            }
            consecutiveFailures.remove(id);
        } catch (RuntimeException adapterFailure) {
            rootSpan.recordError(adapterFailure);
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
    private void handleConfigured(ServiceId id, ServiceStatus current, DesiredState desired, TraceContext parent) {
        List<Dependency> unsatisfiedRequired = new ArrayList<>();
        List<Dependency> unsatisfiedOptional = new ArrayList<>();
        for (Dependency dependency : desired.manifest().dependencies()) {
            boolean running = registryStorePort.findStatus(dependency.serviceId())
                    .map(status -> status.phase() == Phase.RUNNING)
                    .orElse(false);
            if (!running) {
                (dependency.required() ? unsatisfiedRequired : unsatisfiedOptional).add(dependency);
                // Metrique minimale V1 (voir docs/architecture/15-observabilite.md,
                // "egen_dependency_unresolved_total{service, dependency}").
                observabilityPort.incrementCounter("egen_dependency_unresolved_total",
                        Map.of("service", id.value(), "dependency", dependency.serviceId().value(),
                                "required", String.valueOf(dependency.required())));
            }
        }

        if (!unsatisfiedRequired.isEmpty()) {
            Condition condition = new Condition("DependenciesSatisfied", ConditionStatus.FALSE,
                    "RequiredDependencyNotRunning", "en attente de : " + names(unsatisfiedRequired),
                    Instant.now());
            reportCondition(id, condition);
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
        reportCondition(id, dependenciesCondition);

        withChildSpan(parent, "deployment.create",
                () -> deploymentPort.create(id, desired.manifest().deployment()));
        // Premier appel reseau reel effectue pour cette generation : le lien
        // de trace avec l'appel Declare d'origine a rempli son role (voir
        // docs/architecture/15-observabilite.md et PendingDeclareTraces).
        pendingDeclareTraces.clear(id);
        advanceTo(id, current, Phase.DEPLOYING, desired.generation(),
                mergeConditions(current.conditions(), dependenciesCondition));
        workQueue.requeueAfter(id, POLL_DELAY_WHILE_CONVERGING);
    }

    private static String names(List<Dependency> dependencies) {
        return dependencies.stream().map(d -> d.serviceId().value()).collect(Collectors.joining(", "));
    }

    private void observeAndConverge(ServiceId id, ServiceStatus current, DesiredState desired, TraceContext parent) {
        // Filet de securite : si ce cycle atteint DEPLOYING/RUNNING/DEGRADED
        // sans etre jamais passe par handleConfigured pour cette generation
        // dans ce processus (ex. redemarrage du Kernel en cours de
        // convergence), le lien de trace n'a jamais ete purge — no-op sinon.
        pendingDeclareTraces.clear(id);
        // Level-triggered : on re-delegue (idempotent) avant d'observer, pour
        // corriger tout ecart meme si un cycle precedent a ete interrompu.
        withChildSpan(parent, "deployment.create",
                () -> deploymentPort.create(id, desired.manifest().deployment()));
        DeploymentObservation deploymentObservation = deploymentPort.getStatus(id);

        // Enregistrement Discovery de chaque allocation saine et joignable
        // (idempotent, voir DiscoveryPort). Le nettoyage fin d'une allocation
        // qui disparait EN COURS de vie (rescheduling Nomad) est laisse a
        // l'expiration du health check Consul en V2 — seul l'arret explicite
        // (STOPPING, voir handleStopping) deregistre precisement (voir 09.3).
        withChildSpan(parent, "discovery.register", () -> {
            for (AllocationInfo allocation : deploymentPort.listAllocations(id)) {
                if ("running".equals(allocation.status()) && allocation.hasNetworkInfo()) {
                    discoveryPort.register(new ServiceInstanceRegistration(id, allocation.allocationId(),
                            allocation.address(), allocation.port(), desired.manifest().health()));
                }
            }
        });
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

        Condition deploymentCondition = new Condition("DeploymentReady",
                deploymentConverged ? ConditionStatus.TRUE : ConditionStatus.FALSE,
                deploymentConverged ? "Converged" : "AwaitingConvergence",
                "desired=%d healthy=%d running=%d failed=%d".formatted(
                        deploymentObservation.desiredCount(), deploymentObservation.healthyCount(),
                        deploymentObservation.runningCount(), deploymentObservation.failedCount()),
                Instant.now());
        Condition discoveryCondition = new Condition("DiscoveryReady",
                discoveryConverged ? ConditionStatus.TRUE : ConditionStatus.FALSE,
                discoveryConverged ? "Converged" : "AwaitingConvergence",
                "healthyInstances=%d desired=%d".formatted(
                        discoveryObservation.healthyInstanceCount(), deploymentObservation.desiredCount()),
                Instant.now());
        reportCondition(id, deploymentCondition);
        reportCondition(id, discoveryCondition);
        List<Condition> conditions = mergeConditions(current.conditions(),
                List.of(deploymentCondition, discoveryCondition));

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
    private void handleStopping(ServiceId id, ServiceStatus current, DesiredState desired, TraceContext parent) {
        Instant deregisteredAt = stoppingDeregisteredAt.get(id);
        Duration gracePeriod = desired.manifest().lifecycle().shutdownGracePeriod();

        if (deregisteredAt == null) {
            withChildSpan(parent, "discovery.deregister", () -> {
                for (AllocationInfo allocation : deploymentPort.listAllocations(id)) {
                    discoveryPort.deregister(id, allocation.allocationId());
                }
            });
            // Premier appel reseau reel de ce Stop : voir le meme
            // raisonnement que dans handleConfigured.
            pendingDeclareTraces.clear(id);
            stoppingDeregisteredAt.put(id, Instant.now());
            workQueue.requeueAfter(id, gracePeriod);
            return;
        }

        if (Instant.now().isBefore(deregisteredAt.plus(gracePeriod))) {
            workQueue.requeueAfter(id, STOPPING_POLL_DELAY);
            return;
        }

        withChildSpan(parent, "deployment.stop", () -> deploymentPort.stop(id));
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
        observabilityPort.incrementCounter("egen_services_total", Map.of("phase", phase.name()));
    }

    private void reportCondition(ServiceId id, Condition condition) {
        observabilityPort.reportCondition(id, condition);
    }

    /** Execute {@code action} sous un span enfant nomme — voir startSpan/close, docs/architecture/15-observabilite.md. */
    private void withChildSpan(TraceContext parent, String name, Runnable action) {
        TraceSpan span = observabilityPort.startSpan(name, parent);
        try {
            action.run();
        } catch (RuntimeException e) {
            span.recordError(e);
            throw e;
        } finally {
            span.close();
        }
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
        observabilityPort.incrementCounter("egen_reconcile_failures_total",
                Map.of("service", id.value(), "reason", failure.getClass().getSimpleName()));

        if (failures >= MAX_CONSECUTIVE_FAILURES
                && stateMachine.isTransitionAllowed(current.phase(), Phase.FAILED)) {
            Condition failedCondition = new Condition("DeploymentReady", ConditionStatus.FALSE,
                    "AdapterFailureThresholdExceeded", failure.getMessage(), Instant.now());
            reportCondition(id, failedCondition);
            advanceTo(id, current, Phase.FAILED, desired.generation(),
                    mergeConditions(current.conditions(), failedCondition));
            consecutiveFailures.remove(id);
            return;
        }
        long backoffSeconds = Math.min(60, (long) Math.pow(2, failures));
        workQueue.requeueAfter(id, Duration.ofSeconds(backoffSeconds));
    }
}
