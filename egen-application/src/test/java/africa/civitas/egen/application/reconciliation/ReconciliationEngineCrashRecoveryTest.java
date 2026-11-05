package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chaos de niveau 6a (docs/architecture/17-strategie-de-tests.md) : <b>simulation déterministe de la mort
 * brutale du processus</b>, sans aucune infrastructure.
 *
 * <p><b>Principe.</b> La boucle de réconciliation repose sur une promesse : « chaque opération peut être
 * rejouée sans dommage, donc un Kernel qui meurt n'importe quand reprend là où l'état persisté le laisse »
 * (docs/architecture/04-moteur-de-reconciliation.md, « Idempotence »). Ce test la vérifie pour de bon.
 * Il compte d'abord tous les appels qu'un déploiement complet fait à ses ports (déploiement, découverte,
 * registre). Puis, pour <b>chaque</b> appel numéro k, il rejoue le scénario en faisant « mourir » le
 * processus juste <em>avant</em> cet appel, puis juste <em>après</em> son effet (l'effet est donc persisté,
 * mais le moteur n'a pas la suite). Un <b>nouveau</b> moteur, avec une file de travail vide et aucune
 * mémoire, reprend alors sur le même registre et les mêmes faux ports.</p>
 *
 * <p><b>Le moteur rejoue tout, à chaque cycle.</b> Une trace d'un déploiement sans panne montre que, même en
 * régime permanent, chaque cycle rappelle {@code create()}, ré-enregistre les instances dans Discovery et
 * ré-enregistre le statut : c'est la réconciliation « level-triggered » (état désiré réappliqué en continu).
 * Le nombre d'appels à {@code create()} n'est donc <em>pas</em> un invariant ; ce qui doit rester vrai, c'est
 * que le service converge et que Discovery contienne exactement les instances voulues, sans doublon.</p>
 *
 * <p><b>La mort est une {@link Error}</b>, jamais une exception : le moteur ne l'attrape pas comme un échec
 * d'adapter, exactement comme un processus tué n'exécute plus rien.</p>
 *
 * <p><b>Ce que cela prouve, et ne prouve pas.</b> Cela prouve l'idempotence et la reprise de la logique du
 * moteur, avec des ports qui se comportent bien. Cela ne prouve rien sur le comportement de vrais Nomad,
 * Kubernetes ou Consul sous panne : c'est l'objet du niveau 6b (infrastructure réelle), à faire.</p>
 */
class ReconciliationEngineCrashRecoveryTest {

    private static final ServiceId SERVICE_ID = ServiceId.of("news-service");
    private static final Duration SHORT_GRACE_PERIOD = Duration.ofMillis(20);
    /** Cycles que travaille le premier Kernel : assez pour traverser tout le déploiement, puis rester en régime permanent. */
    private static final int FIRST_KERNEL_CYCLES = 7;
    /** Cycles laissés au Kernel redémarré pour ramener le service à l'état attendu. */
    private static final int MAX_CYCLES = 20;
    /**
     * Pause entre deux cycles pendant un arrêt gracieux : l'arrêt dépend d'une horloge (délai de grâce) et le
     * marqueur « désinscrit à tel instant » est gardé en mémoire par le moteur, donc perdu si le Kernel meurt.
     * Un Kernel neuf recommence alors par la désinscription (idempotente) puis attend de nouveau le délai.
     */
    private static final long STOP_PAUSE_MILLIS = SHORT_GRACE_PERIOD.toMillis() + 10;

    /** Le processus est tué : une Error, que le moteur ne doit jamais traiter comme un échec d'adapter. */
    private static final class ProcessKilled extends Error {
        ProcessKilled(String where) {
            super(where);
        }
    }

    /** Tout ce qui survit à la mort du processus : le registre persisté et les moteurs d'exécution et de découverte. */
    private static final class World {
        final InMemoryRegistryStore registry = new InMemoryRegistryStore();
        final FakeDeploymentPort deployment = new FakeDeploymentPort();
        final FakeDiscoveryPort discovery = new FakeDiscoveryPort();

        World() {
            registry.save(new DesiredState(manifest(), 0L, TargetEnvironment.of("test")));
        }

        ReconciliationEngine engine(RegistryStorePort registryPort, DeploymentPort deploymentPort,
                                    DiscoveryPort discoveryPort) {
            return new ReconciliationEngine(new WorkQueue(), registryPort, deploymentPort, discoveryPort,
                    new NoOpObservabilityPort());
        }

        /** Un moteur tout neuf, branché directement sur les survivants : le Kernel redémarré. */
        ReconciliationEngine restartedEngine() {
            return engine(registry, deployment, discovery);
        }

        Phase phase() {
            return registry.findStatus(SERVICE_ID).map(ServiceStatus::phase).orElse(null);
        }
    }

    /** Fait mourir le processus au k-ième appel à un port, avant ou après que l'appel ait pris effet. */
    private static final class CrashPlan {
        final int crashAt;
        final boolean afterEffect;
        final List<String> trace = new ArrayList<>();
        boolean fired;

        CrashPlan(int crashAt, boolean afterEffect) {
            this.crashAt = crashAt;
            this.afterEffect = afterEffect;
        }

        int calls() {
            return trace.size();
        }

        <T> T wrap(Class<T> port, T delegate) {
            InvocationHandler handler = (proxy, method, args) -> {
                String call = port.getSimpleName() + "." + method.getName();
                trace.add(call);
                boolean thisIsTheOne = trace.size() == crashAt;
                if (thisIsTheOne && !afterEffect) {
                    fired = true;
                    throw new ProcessKilled("avant l'appel n°" + crashAt + " (" + call + ")");
                }
                Object result;
                try {
                    result = method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
                if (thisIsTheOne) {
                    fired = true;
                    throw new ProcessKilled("après l'effet de l'appel n°" + crashAt + " (" + call + ")");
                }
                return result;
            };
            return port.cast(Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[] {port}, handler));
        }
    }

    private static ServiceManifest manifest() {
        return new ServiceManifest(SERVICE_ID, ServiceVersion.parse("2.4.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry.civitas.africa/news-service:2.4.0", "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0", "500m", "512Mi",
                        new ReplicaRange(2, 6)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(SHORT_GRACE_PERIOD));
    }

    /** Un « premier Kernel » qui travaille jusqu'à sa mort (ou jusqu'à {@code cycles} cycles, s'il n'est pas tué). */
    private static CrashPlan runFirstKernel(World world, CrashPlan plan, int cycles) {
        return runFirstKernel(world, plan, cycles, 0L);
    }

    private static CrashPlan runFirstKernel(World world, CrashPlan plan, int cycles, long pauseMillis) {
        ReconciliationEngine first = world.engine(
                plan.wrap(RegistryStorePort.class, world.registry),
                plan.wrap(DeploymentPort.class, world.deployment),
                plan.wrap(DiscoveryPort.class, world.discovery));
        try {
            for (int i = 0; i < cycles; i++) {
                first.reconcile(SERVICE_ID);
                pause(pauseMillis);
            }
        } catch (ProcessKilled killed) {
            // le processus est mort : plus rien ne s'exécute dans ce moteur
        }
        return plan;
    }

    private static void reconcileUntil(World world, Phase target) {
        reconcileUntil(world, target, 0L);
    }

    private static void reconcileUntil(World world, Phase target, long pauseMillis) {
        ReconciliationEngine restarted = world.restartedEngine();
        for (int i = 0; i < MAX_CYCLES && world.phase() != target; i++) {
            restarted.reconcile(SERVICE_ID);
            pause(pauseMillis);
        }
    }

    private static void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test interrompu", e);
        }
    }

    // ------------------------------------------------------------------ déploiement complet

    @Test
    void aKernelKilledAtAnyPointOfADeploymentIsRecoveredByAFreshOneWithoutDuplicates() {
        // 1. Combien d'appels de ports un déploiement sans panne fait-il ? Chacun est un point de mort possible.
        World reference = new World();
        CrashPlan baseline = runFirstKernel(reference, new CrashPlan(Integer.MAX_VALUE, false), FIRST_KERNEL_CYCLES);
        assertEquals(Phase.RUNNING, reference.phase(), "sans panne, le service doit arriver en RUNNING");
        int crashPoints = baseline.calls();
        assertTrue(crashPoints > 10, "le balayage serait vide : seulement " + crashPoints + " appels comptés");

        // 2. Pour chaque point et chaque variante, tuer le premier Kernel puis laisser un Kernel neuf reprendre.
        List<String> violations = new ArrayList<>();
        int scenarios = 0;
        for (int k = 1; k <= crashPoints; k++) {
            for (boolean afterEffect : new boolean[] {false, true}) {
                World world = new World();
                CrashPlan plan = runFirstKernel(world, new CrashPlan(k, afterEffect), FIRST_KERNEL_CYCLES);
                scenarios++;
                String label = "mort " + (afterEffect ? "après l'effet" : "avant l'appel") + " n°" + k
                        + " (" + plan.trace.get(k - 1) + ")";

                if (!plan.fired) {
                    violations.add(label + " : la panne n'a jamais été déclenchée, le scénario ne teste rien");
                    continue;
                }
                reconcileUntil(world, Phase.RUNNING);

                if (world.phase() != Phase.RUNNING) {
                    violations.add(label + " : le Kernel neuf n'a pas ramené le service en RUNNING en " + MAX_CYCLES
                            + " cycles (phase finale : " + world.phase() + ")");
                } else if (world.discovery.registeredCount(SERVICE_ID) != 2) {
                    violations.add(label + " : " + world.discovery.registeredCount(SERVICE_ID)
                            + " instances enregistrées dans Discovery au lieu de 2 (doublon ou perte)");
                } else if (world.deployment.createCallCount(SERVICE_ID) < 1) {
                    violations.add(label + " : le déploiement n'a jamais été demandé (create() jamais appelé)");
                }
            }
        }

        assertTrue(violations.isEmpty(), scenarios + " scénarios de mort brutale balayés, "
                + violations.size() + " violation(s) :\n  " + String.join("\n  ", violations));
    }

    // ------------------------------------------------------------------ arrêt gracieux

    @Test
    void aKernelKilledAtAnyPointOfAGracefulStopIsRecoveredByAFreshOneWithNoInstanceLeftRegistered() {
        // Point de départ commun : un service RUNNING auquel l'opérateur vient de demander l'arrêt (phase STOPPING).
        World reference = new World();
        runFirstKernel(reference, new CrashPlan(Integer.MAX_VALUE, false), FIRST_KERNEL_CYCLES);
        assertEquals(Phase.RUNNING, reference.phase());
        requestStop(reference);
        CrashPlan baseline = runFirstKernel(reference, new CrashPlan(Integer.MAX_VALUE, false), FIRST_KERNEL_CYCLES,
                STOP_PAUSE_MILLIS);
        assertEquals(Phase.STOPPED, reference.phase(), "sans panne, l'arrêt doit aboutir en STOPPED");
        int crashPoints = baseline.calls();
        assertTrue(crashPoints > 3, "le balayage serait vide : seulement " + crashPoints + " appels comptés");

        List<String> violations = new ArrayList<>();
        int scenarios = 0;
        for (int k = 1; k <= crashPoints; k++) {
            for (boolean afterEffect : new boolean[] {false, true}) {
                World world = new World();
                runFirstKernel(world, new CrashPlan(Integer.MAX_VALUE, false), FIRST_KERNEL_CYCLES); // RUNNING, sans panne
                requestStop(world);
                CrashPlan plan = runFirstKernel(world, new CrashPlan(k, afterEffect), FIRST_KERNEL_CYCLES,
                        STOP_PAUSE_MILLIS);
                scenarios++;
                String label = "mort " + (afterEffect ? "après l'effet" : "avant l'appel") + " n°" + k
                        + " pendant l'arrêt (" + plan.trace.get(k - 1) + ")";

                if (!plan.fired) {
                    violations.add(label + " : la panne n'a jamais été déclenchée");
                    continue;
                }
                reconcileUntil(world, Phase.STOPPED, STOP_PAUSE_MILLIS);

                if (world.phase() != Phase.STOPPED) {
                    violations.add(label + " : le Kernel neuf n'a pas mené l'arrêt à STOPPED (phase finale : "
                            + world.phase() + ")");
                } else if (world.discovery.registeredCount(SERVICE_ID) != 0) {
                    violations.add(label + " : " + world.discovery.registeredCount(SERVICE_ID)
                            + " instance(s) encore enregistrée(s) dans Discovery après l'arrêt");
                }
            }
        }

        assertTrue(violations.isEmpty(), scenarios + " scénarios de mort brutale pendant l'arrêt balayés, "
                + violations.size() + " violation(s) :\n  " + String.join("\n  ", violations));
    }

    /** Ce que fait le use case d'arrêt : persister la phase STOPPING ; la réconciliation fait le reste. */
    private static void requestStop(World world) {
        ServiceStatus current = world.registry.findStatus(SERVICE_ID).orElseThrow();
        world.registry.saveStatus(SERVICE_ID,
                new ServiceStatus(Phase.STOPPING, current.conditions(), current.observedGeneration()));
    }
}
