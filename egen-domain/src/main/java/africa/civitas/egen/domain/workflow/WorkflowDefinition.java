package africa.civitas.egen.domain.workflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Aggregate root — voir docs/architecture/05-modele-de-domaine.md
 * ({@code WorkflowDefinition}) et docs/architecture/11-moteur-de-workflow.md
 * (exemple YAML complet). Orchestre des APPELS a des operations exposees par
 * des services — ne contient jamais elle-meme de logique metier (garde-fou
 * explicite, voir docs/architecture/18-anti-patterns.md, "Workflow avec
 * logique metier embarquee").
 *
 * <p>Invariants verifies a la construction, jamais dans une couche separee
 * (voir docs/architecture/05-modele-de-domaine.md, "Regle de conception") :
 * au moins une etape, identifiants d'etape uniques, {@code dependsOn} ne
 * references que des etapes connues de CETTE definition, le graphe forme
 * par {@code dependsOn} est acyclique (meme discipline que
 * {@link africa.civitas.egen.domain.dependency.DependencyGraph} pour les
 * services), et {@code executionPolicy.manualApprovalSteps} ne reference
 * que des etapes connues.</p>
 */
public final class WorkflowDefinition {

    private final WorkflowId id;
    private final Map<StepId, Step> stepsById;
    private final CompensationStrategy compensationStrategy;
    private final ExecutionPolicy executionPolicy;

    private WorkflowDefinition(WorkflowId id, Map<StepId, Step> stepsById,
                                CompensationStrategy compensationStrategy, ExecutionPolicy executionPolicy) {
        this.id = id;
        this.stepsById = stepsById;
        this.compensationStrategy = compensationStrategy;
        this.executionPolicy = executionPolicy;
    }

    public static WorkflowDefinition of(WorkflowId id, List<Step> steps,
                                         CompensationStrategy compensationStrategy, ExecutionPolicy executionPolicy) {
        if (id == null) {
            throw new IllegalArgumentException("WorkflowDefinition.id est obligatoire");
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("WorkflowDefinition doit declarer au moins une etape");
        }
        if (compensationStrategy == null) {
            throw new IllegalArgumentException("WorkflowDefinition.compensationStrategy est obligatoire");
        }
        if (executionPolicy == null) {
            throw new IllegalArgumentException("WorkflowDefinition.executionPolicy est obligatoire");
        }

        Map<StepId, Step> stepsById = new LinkedHashMap<>();
        for (Step step : steps) {
            if (stepsById.putIfAbsent(step.id(), step) != null) {
                throw new IllegalArgumentException("Identifiant d'etape duplique : \"" + step.id() + "\"");
            }
        }
        for (Step step : steps) {
            for (StepId dependency : step.dependsOn()) {
                if (!stepsById.containsKey(dependency)) {
                    throw new IllegalArgumentException("Etape \"" + step.id() + "\" depend d'une etape inconnue : \""
                            + dependency + "\"");
                }
            }
        }
        for (StepId approvalStep : executionPolicy.manualApprovalSteps()) {
            if (!stepsById.containsKey(approvalStep)) {
                throw new IllegalArgumentException(
                        "executionPolicy.manualApprovalSteps reference une etape inconnue : \"" + approvalStep + "\"");
            }
        }

        detectCycleOrThrow(stepsById);

        return new WorkflowDefinition(id, Map.copyOf(stepsById), compensationStrategy, executionPolicy);
    }

    public WorkflowId id() {
        return id;
    }

    public List<Step> steps() {
        return List.copyOf(stepsById.values());
    }

    public Optional<Step> stepById(StepId stepId) {
        return Optional.ofNullable(stepsById.get(stepId));
    }

    public CompensationStrategy compensationStrategy() {
        return compensationStrategy;
    }

    public ExecutionPolicy executionPolicy() {
        return executionPolicy;
    }

    /**
     * Tri topologique des etapes (algorithme de Kahn — meme approche que
     * {@link africa.civitas.egen.domain.dependency.DependencyGraph#topologicalOrder()}) :
     * chaque etape apparait apres toutes celles dont elle depend. Sert de
     * base a l'ordonnancement du moteur (voir
     * docs/architecture/11-moteur-de-workflow.md).
     */
    public List<StepId> topologicalOrder() {
        Map<StepId, Integer> remainingDependencies = new HashMap<>();
        Map<StepId, List<StepId>> dependents = new HashMap<>();
        for (Step step : stepsById.values()) {
            remainingDependencies.put(step.id(), step.dependsOn().size());
            for (StepId dependency : step.dependsOn()) {
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(step.id());
            }
        }

        Queue<StepId> ready = new ArrayDeque<>();
        remainingDependencies.forEach((stepId, count) -> {
            if (count == 0) {
                ready.add(stepId);
            }
        });

        List<StepId> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            StepId current = ready.poll();
            order.add(current);
            for (StepId dependent : dependents.getOrDefault(current, List.of())) {
                int updated = remainingDependencies.merge(dependent, -1, Integer::sum);
                if (updated == 0) {
                    ready.add(dependent);
                }
            }
        }
        // Un cycle a deja ete refuse a la construction (of()) : order couvre
        // donc toujours l'integralite des etapes ici.
        return List.copyOf(order);
    }

    private static void detectCycleOrThrow(Map<StepId, Step> stepsById) {
        Set<StepId> visited = new HashSet<>();
        Set<StepId> onStack = new LinkedHashSet<>(); // preserve l'ordre pour le message d'erreur

        for (StepId start : stepsById.keySet()) {
            if (!visited.contains(start)) {
                depthFirstSearch(start, stepsById, visited, onStack);
            }
        }
    }

    private static void depthFirstSearch(StepId current, Map<StepId, Step> stepsById,
                                          Set<StepId> visited, Set<StepId> onStack) {
        visited.add(current);
        onStack.add(current);

        for (StepId dependency : stepsById.get(current).dependsOn()) {
            if (onStack.contains(dependency)) {
                List<StepId> cyclePath = new ArrayList<>(onStack);
                int startIndex = cyclePath.indexOf(dependency);
                cyclePath = cyclePath.subList(startIndex, cyclePath.size());
                cyclePath.add(dependency); // referme le cycle dans le message
                throw new CyclicStepDependencyException(cyclePath);
            }
            if (!visited.contains(dependency)) {
                depthFirstSearch(dependency, stepsById, visited, onStack);
            }
        }
        onStack.remove(current);
    }
}
