package africa.civitas.egen.domain.dependency;

import africa.civitas.egen.domain.model.ServiceId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyGraphTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final ServiceId NOTIFICATION = ServiceId.of("notification-service");
    private static final ServiceId STORAGE = ServiceId.of("storage-service");

    @Test
    void buildsAValidAcyclicGraph() {
        DependencyGraph graph = DependencyGraph.of(Map.of(
                NEWS, Set.of(NOTIFICATION, STORAGE),
                NOTIFICATION, Set.of(),
                STORAGE, Set.of()));

        assertEquals(Set.of(NOTIFICATION, STORAGE), graph.dependenciesOf(NEWS));
        assertTrue(graph.dependenciesOf(NOTIFICATION).isEmpty());
    }

    @Test
    void topologicalOrderPlacesEachServiceAfterItsDependencies() {
        DependencyGraph graph = DependencyGraph.of(Map.of(
                NEWS, Set.of(NOTIFICATION, STORAGE),
                NOTIFICATION, Set.of(STORAGE),
                STORAGE, Set.of()));

        List<ServiceId> order = graph.topologicalOrder();

        assertEquals(3, order.size());
        assertTrue(order.indexOf(STORAGE) < order.indexOf(NOTIFICATION));
        assertTrue(order.indexOf(NOTIFICATION) < order.indexOf(NEWS));
    }

    @Test
    void rejectsADirectCycle() {
        assertThrows(CyclicDependencyException.class, () -> DependencyGraph.of(Map.of(
                NEWS, Set.of(NOTIFICATION),
                NOTIFICATION, Set.of(NEWS))));
    }

    @Test
    void rejectsAnIndirectCycleAcrossThreeServices() {
        // news -> notification -> storage -> news
        assertThrows(CyclicDependencyException.class, () -> DependencyGraph.of(Map.of(
                NEWS, Set.of(NOTIFICATION),
                NOTIFICATION, Set.of(STORAGE),
                STORAGE, Set.of(NEWS))));
    }

    @Test
    void anEmptyGraphIsValidAndHasAnEmptyTopologicalOrder() {
        DependencyGraph graph = DependencyGraph.of(Map.of());
        assertTrue(graph.topologicalOrder().isEmpty());
    }
}
