package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.MessagingPort;
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

    @ConfigProperty(name = "egen.workflow.retry.base-backoff-millis", defaultValue = "500")
    long baseBackoffMillis;

    @ConfigProperty(name = "egen.workflow.retry.max-backoff-millis", defaultValue = "30000")
    long maxBackoffMillis;

    private ExecutorService executor;
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
                    resiliencyPolicyProvider);
        }
        return engine;
    }

    void onStart(@Observes StartupEvent event) {
        engine().recoverInterruptedExecutions();
    }

    void onStop(@Observes ShutdownEvent event) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
