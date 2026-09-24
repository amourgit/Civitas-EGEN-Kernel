package africa.civitas.egen.domain.dependency;

import africa.civitas.egen.domain.model.ServiceId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Graphe oriente des dependances techniques declarees entre services (voir
 * docs/architecture/10-gestion-des-dependances.md). Construit au niveau de
 * l'ecosysteme ENTIER, jamais localement a un seul manifeste — c'est ce qui
 * permet de detecter un cycle qui traverserait trois services ou plus.
 *
 * <p>Refuse de se construire si le graphe contient un cycle (garde-fou
 * applique a la construction, comme tout Value Object du domaine — voir
 * docs/architecture/05-modele-de-domaine.md).</p>
 */
public final class DependencyGraph {

    private final Map<ServiceId, Set<ServiceId>> dependsOn;

    private DependencyGraph(Map<ServiceId, Set<ServiceId>> dependsOn) {
        this.dependsOn = dependsOn;
    }

    /**
     * @param dependsOnEdges pour chaque service connu, l'ensemble des
     *                       services dont il depend (toutes dependances
     *                       confondues, required ou non — un cycle est
     *                       invalide quel que soit ce statut)
     * @throws CyclicDependencyException si le graphe contient un cycle
     */
    public static DependencyGraph of(Map<ServiceId, Set<ServiceId>> dependsOnEdges) {
        Map<ServiceId, Set<ServiceId>> copy = new HashMap<>();
        for (Map.Entry<ServiceId, Set<ServiceId>> entry : dependsOnEdges.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        Map<ServiceId, Set<ServiceId>> immutable = Map.copyOf(copy);
        detectCycleOrThrow(immutable);
        return new DependencyGraph(immutable);
    }

    public Set<ServiceId> nodes() {
        return dependsOn.keySet();
    }

    public Set<ServiceId> dependenciesOf(ServiceId id) {
        return dependsOn.getOrDefault(id, Set.of());
    }

    /**
     * Tri topologique (algorithme de Kahn) : chaque service apparait apres
     * tous ceux dont il depend. Utilise par le planificateur de deploiement
     * pour l'ordonnancement (voir
     * docs/architecture/10-gestion-des-dependances.md, "Ordonnancement du
     * deploiement").
     */
    public List<ServiceId> topologicalOrder() {
        Map<ServiceId, Integer> remainingDependencies = new HashMap<>();
        Map<ServiceId, List<ServiceId>> dependents = new HashMap<>();
        for (ServiceId node : dependsOn.keySet()) {
            remainingDependencies.put(node, dependenciesOf(node).size());
            for (ServiceId dependency : dependenciesOf(node)) {
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(node);
            }
        }

        Queue<ServiceId> ready = new ArrayDeque<>();
        remainingDependencies.forEach((node, count) -> {
            if (count == 0) {
                ready.add(node);
            }
        });

        List<ServiceId> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            ServiceId node = ready.poll();
            order.add(node);
            for (ServiceId dependent : dependents.getOrDefault(node, List.of())) {
                int updated = remainingDependencies.merge(dependent, -1, Integer::sum);
                if (updated == 0) {
                    ready.add(dependent);
                }
            }
        }
        // Un cycle a deja ete refuse a la construction (of()) : order couvre
        // donc toujours l'integralite des noeuds ici.
        return List.copyOf(order);
    }

    private static void detectCycleOrThrow(Map<ServiceId, Set<ServiceId>> edges) {
        Set<ServiceId> visited = new HashSet<>();
        Set<ServiceId> onStack = new LinkedHashSet<>(); // prserve l'ordre pour le message d'erreur

        for (ServiceId start : edges.keySet()) {
            if (!visited.contains(start)) {
                depthFirstSearch(start, edges, visited, onStack);
            }
        }
    }

    private static void depthFirstSearch(ServiceId current, Map<ServiceId, Set<ServiceId>> edges,
                                          Set<ServiceId> visited, Set<ServiceId> onStack) {
        visited.add(current);
        onStack.add(current);

        for (ServiceId dependency : edges.getOrDefault(current, Set.of())) {
            if (onStack.contains(dependency)) {
                List<ServiceId> cyclePath = new ArrayList<>(onStack);
                int startIndex = cyclePath.indexOf(dependency);
                cyclePath = cyclePath.subList(startIndex, cyclePath.size());
                cyclePath.add(dependency); // referme le cycle dans le message
                throw new CyclicDependencyException(cyclePath);
            }
            if (!visited.contains(dependency)) {
                depthFirstSearch(dependency, edges, visited, onStack);
            }
        }
        onStack.remove(current);
    }
}
