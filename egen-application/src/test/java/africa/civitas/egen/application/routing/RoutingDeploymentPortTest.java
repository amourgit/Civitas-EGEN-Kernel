package africa.civitas.egen.application.routing;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentException;
import africa.civitas.egen.application.port.DeploymentHandle;
import africa.civitas.egen.application.port.DeploymentPort;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.model.DeploymentObservation;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Niveau 1 (docs/architecture/17) : le routeur choisit le bon adapter et ne fait rien d'autre. */
class RoutingDeploymentPortTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final ServiceId MAIL = ServiceId.of("mail-service");

    /** Faux adapter : note chaque appel sous la forme « operation:serviceId ». */
    private static final class Recording implements DeploymentPort {
        final String name;
        final List<String> calls = new ArrayList<>();

        Recording(String name) {
            this.name = name;
        }

        @Override public DeploymentHandle create(ServiceId id, DeploymentSpec spec) {
            calls.add("create:" + id);
            return new DeploymentHandle(id, name + "-" + id);
        }
        @Override public void update(ServiceId id, DeploymentSpec spec) { calls.add("update:" + id); }
        @Override public void scale(ServiceId id, int replicas) { calls.add("scale:" + id + ":" + replicas); }
        @Override public void restart(ServiceId id) { calls.add("restart:" + id); }
        @Override public void stop(ServiceId id) { calls.add("stop:" + id); }
        @Override public void remove(ServiceId id) { calls.add("remove:" + id); }
        @Override public DeploymentObservation getStatus(ServiceId id) {
            calls.add("getStatus:" + id);
            return new DeploymentObservation(2, 2, 2, 0, Instant.EPOCH);
        }
        @Override public List<AllocationInfo> listAllocations(ServiceId id) {
            calls.add("listAllocations:" + id);
            return List.of(new AllocationInfo(name + "-alloc", "running", Instant.EPOCH, "10.0.0.1", 8080));
        }
        @Override public void rollback(ServiceId id, ServiceVersion version) {
            calls.add("rollback:" + id + ":" + version);
        }
    }

    private final Recording nomad = new Recording("nomad");
    private final Recording kubernetes = new Recording("kubernetes");
    private InMemoryRegistryStore registry;
    private RoutingDeploymentPort router;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistryStore();
        router = new RoutingDeploymentPort(Map.of("nomad", nomad, "kubernetes", kubernetes), registry);
        declare(NEWS, "nomad");
        declare(MAIL, "kubernetes");
    }

    private static DeploymentSpec spec(String adapter) {
        return new DeploymentSpec(adapter, "registry/x:1.0.0", "500m", "512Mi", new ReplicaRange(1, 2));
    }

    private void declare(ServiceId id, String adapter) {
        ServiceManifest manifest = new ServiceManifest(id, ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry/x:1.0.0", "java"), spec(adapter),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                LifecyclePolicy.defaultPolicy());
        registry.save(new DesiredState(manifest, 0L, TargetEnvironment.of("test")));
    }

    @Test
    void createAndUpdateRouteBySpecAdapter() {
        router.create(NEWS, spec("nomad"));
        router.update(MAIL, spec("kubernetes"));

        assertEquals(List.of("create:news-service"), nomad.calls);
        assertEquals(List.of("update:mail-service"), kubernetes.calls);
    }

    @Test
    void everyOtherOperationRoutesByTheRegisteredManifestAndOnlyReachesThatAdapter() {
        router.scale(MAIL, 3);
        router.restart(MAIL);
        router.stop(MAIL);
        router.remove(MAIL);
        router.rollback(MAIL, ServiceVersion.parse("0.9.0"));
        assertEquals(DeploymentObservation.class, router.getStatus(MAIL).getClass());
        assertEquals("kubernetes-alloc", router.listAllocations(MAIL).get(0).allocationId());

        assertEquals(List.of("scale:mail-service:3", "restart:mail-service", "stop:mail-service",
                "remove:mail-service", "rollback:mail-service:0.9.0", "getStatus:mail-service",
                "listAllocations:mail-service"), kubernetes.calls);
        assertTrue(nomad.calls.isEmpty(), "l'autre adapter ne doit recevoir aucun appel : " + nomad.calls);
    }

    @Test
    void twoServicesOnTwoAdaptersNeverCrossOver() {
        router.stop(NEWS);
        router.stop(MAIL);

        assertEquals(List.of("stop:news-service"), nomad.calls);
        assertEquals(List.of("stop:mail-service"), kubernetes.calls);
    }

    @Test
    void readsOnAServiceAbsentFromTheRegistryAreEmptyNotErrors() {
        ServiceId ghost = ServiceId.of("ghost-service");

        assertEquals(DeploymentObservation.empty(), router.getStatus(ghost));
        assertTrue(router.listAllocations(ghost).isEmpty());
        assertTrue(nomad.calls.isEmpty() && kubernetes.calls.isEmpty());
    }

    @Test
    void writesOnAServiceAbsentFromTheRegistryFailWithoutTouchingAnyAdapter() {
        ServiceId ghost = ServiceId.of("ghost-service");

        assertThrows(DeploymentException.class, () -> router.scale(ghost, 1));
        assertThrows(DeploymentException.class, () -> router.restart(ghost));
        assertThrows(DeploymentException.class, () -> router.stop(ghost));
        assertThrows(DeploymentException.class, () -> router.remove(ghost));
        assertThrows(DeploymentException.class, () -> router.rollback(ghost, ServiceVersion.parse("1.0.0")));
        assertTrue(nomad.calls.isEmpty() && kubernetes.calls.isEmpty());
    }

    @Test
    void anAdapterThatIsNotEnabledFailsAndNamesTheEnabledOnesWithNoFallback() {
        DeploymentException e = assertThrows(DeploymentException.class, () -> router.create(NEWS, spec("docker")));

        assertTrue(e.getMessage().contains("docker"), e.getMessage());
        assertTrue(e.getMessage().contains("kubernetes, nomad"), e.getMessage());
        assertTrue(nomad.calls.isEmpty() && kubernetes.calls.isEmpty(), "aucun repli sur un autre moteur");
    }

    @Test
    void aRegisteredServiceWhoseAdapterIsNoLongerEnabledFailsInsteadOfFallingBack() {
        RoutingDeploymentPort nomadOnly = new RoutingDeploymentPort(Map.of("nomad", nomad), registry);

        assertThrows(DeploymentException.class, () -> nomadOnly.getStatus(MAIL)); // MAIL est déclaré « kubernetes »
        assertThrows(DeploymentException.class, () -> nomadOnly.stop(MAIL));
        assertTrue(nomad.calls.isEmpty());
    }

    @Test
    void aRouterWithoutAnyAdapterIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new RoutingDeploymentPort(Map.of(), registry));
        assertThrows(IllegalArgumentException.class, () -> new RoutingDeploymentPort(null, registry));
    }
}
