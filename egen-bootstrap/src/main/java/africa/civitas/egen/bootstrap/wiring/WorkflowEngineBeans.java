package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.ServiceInvocationPort;
import africa.civitas.egen.application.port.WorkflowStorePort;
import africa.civitas.egen.application.resilience.ResiliencyPolicyProvider;
import africa.civitas.egen.application.workflow.Sleeper;
import africa.civitas.egen.application.workflow.WorkflowEngine;
import africa.civitas.egen.application.workflow.WorkflowEnginePort;
import africa.civitas.egen.application.workflow.WorkflowEngineSettings;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cablage et cycle de vie du Moteur de Workflow (voir
 * docs/architecture/11-moteur-de-workflow.md, "Moteur d'execution" et
 * "Reprise au demarrage"). Seul endroit qui connait le choix d'un thread
 * virtuel par execution pilotee : egen-application ne depend que de
 * {@link java.util.concurrent.Executor}.
 *
 * <p>A l'arret, l'executor est ferme sans attendre les executions en cours :
 * leur etat vit deja dans le store, et {@code recoverInterruptedExecutions}
 * les relance au prochain demarrage (c'est tout l'interet de la durabilite).</p>
 */
@ApplicationScoped
public class WorkflowEngineBeans {

    @Inject
    WorkflowStorePort workflowStorePort;

    @Inject
    DiscoveryPort discoveryPort;

    @Inject
    ServiceInvocationPort serviceInvocationPort;

    @Inject
    MessagingPort messagingPort;

    @Inject
    ResiliencyPolicyProvider resiliencyPolicyProvider;

    @Inject
    ObservabilityPort observabilityPort;

    @ConfigProperty(name = "egen.workflow.retry.base-backoff-millis", defaultValue = "500")
    long baseBackoffMillis;

    @ConfigProperty(name = "egen.workflow.retry.max-backoff-millis", defaultValue = "30000")
    long maxBackoffMillis;

    /** Duree du bail de pilotage d'une execution (durée ISO-8601), docs/architecture/11, « Bail d'execution ». */
    @ConfigProperty(name = "egen.workflow.lease-duration", defaultValue = "PT60S")
    Duration leaseDuration;

    /** Identite de ce Kernel comme pilote ; par defaut le nom de la machine et un identifiant aleatoire. */
    @ConfigProperty(name = "egen.kernel.id")
    java.util.Optional<String> kernelId;

    /**
     * Intervalle de la recherche des executions a reprendre (duree ISO-8601). Sans elle, un Kernel qui meurt laisse ses
     * executions orphelines tant qu'un autre Kernel ne redemarre pas ; {@code PT0S} la desactive.
     */
    @ConfigProperty(name = "egen.workflow.recovery-interval", defaultValue = "PT30S")
    Duration recoveryInterval;

    private ExecutorService executor;
    private java.util.concurrent.ScheduledExecutorService recoveryScheduler;
    private WorkflowEngine engine;

    @Produces
    @ApplicationScoped
    public WorkflowEnginePort workflowEnginePort() {
        return engine();
    }

    private synchronized WorkflowEngine engine() {
        if (engine == null) {
            executor = Executors.newVirtualThreadPerTaskExecutor();
            engine = new WorkflowEngine(workflowStorePort, discoveryPort, serviceInvocationPort, messagingPort,
                    Clock.systemUTC(), Sleeper.threadSleep(), executor,
                    new WorkflowEngineSettings(Duration.ofMillis(baseBackoffMillis),
                            Duration.ofMillis(maxBackoffMillis)),
                    resiliencyPolicyProvider, observabilityPort)
                    .lease(kernelId.orElseGet(WorkflowEngineBeans::defaultKernelId), leaseDuration);
        }
        return engine;
    }

    private static String defaultKernelId() {
        String host;
        try {
            host = java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.io.IOException e) {
            host = "kernel";
        }
        return host + "-" + java.util.UUID.randomUUID();
    }

    void onStart(@Observes StartupEvent event) {
        engine().recoverInterruptedExecutions();
        if (!recoveryInterval.isZero() && !recoveryInterval.isNegative()) {
            recoveryScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "egen-workflow-recovery");
                thread.setDaemon(true);
                return thread;
            });
            long millis = recoveryInterval.toMillis();
            recoveryScheduler.scheduleWithFixedDelay(() -> {
                try {
                    engine().recoverInterruptedExecutions();
                } catch (RuntimeException e) {
                    System.getLogger(WorkflowEngineBeans.class.getName()).log(System.Logger.Level.WARNING,
                            "Recherche des executions a reprendre impossible : " + e.getMessage(), e);
                }
            }, millis, millis, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (recoveryScheduler != null) {
            recoveryScheduler.shutdownNow();
        }
        if (engine != null) {
            engine.shutdown();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
