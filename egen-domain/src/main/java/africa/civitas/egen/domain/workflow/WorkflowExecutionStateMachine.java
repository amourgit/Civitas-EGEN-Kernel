package africa.civitas.egen.domain.workflow;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Machine a etats du statut global d'une {@link WorkflowExecution} — meme
 * discipline que
 * {@link africa.civitas.egen.domain.lifecycle.LifecycleStateMachine} pour
 * {@code Phase} : chaque transition non explicitement autorisee ici est
 * rejetee par du code, jamais seulement documentee.
 *
 * <pre>
 * RUNNING            -&gt; PAUSED, COMPLETED, COMPENSATING, FAILED
 * PAUSED             -&gt; RUNNING, FAILED
 * COMPENSATING       -&gt; COMPENSATED, COMPENSATION_FAILED
 * COMPLETED          -&gt; (terminal)
 * FAILED             -&gt; (terminal)
 * COMPENSATED        -&gt; (terminal)
 * COMPENSATION_FAILED-&gt; (terminal — voir docs/architecture/11-moteur-de-workflow.md,
 *                        "visible et alertable, jamais masquee" : aucune
 *                        transition automatique n'en sort, une intervention
 *                        humaine hors du moteur est requise)
 * </pre>
 *
 * <p>{@code RUNNING -&gt; FAILED} directement (sans passer par COMPENSATING)
 * represente une terminaison explicite via
 * {@code WorkflowEnginePort.terminate(id, reason)} — tout echec d'etape,
 * lui, transite toujours par COMPENSATING d'abord (voir
 * docs/architecture/11-moteur-de-workflow.md, "Compensation — le coeur de
 * la robustesse Saga"), meme quand aucune compensation n'est effectivement
 * necessaire (COMPENSATING se resout alors immediatement en COMPENSATED).</p>
 */
public final class WorkflowExecutionStateMachine {

    private static final Map<WorkflowExecutionStatus, Set<WorkflowExecutionStatus>> ALLOWED_TRANSITIONS =
            buildTransitionTable();

    private static Map<WorkflowExecutionStatus, Set<WorkflowExecutionStatus>> buildTransitionTable() {
        Map<WorkflowExecutionStatus, Set<WorkflowExecutionStatus>> table =
                new EnumMap<>(WorkflowExecutionStatus.class);
        table.put(WorkflowExecutionStatus.RUNNING, EnumSet.of(
                WorkflowExecutionStatus.PAUSED, WorkflowExecutionStatus.COMPLETED,
                WorkflowExecutionStatus.COMPENSATING, WorkflowExecutionStatus.FAILED));
        table.put(WorkflowExecutionStatus.PAUSED, EnumSet.of(
                WorkflowExecutionStatus.RUNNING, WorkflowExecutionStatus.FAILED));
        table.put(WorkflowExecutionStatus.COMPENSATING, EnumSet.of(
                WorkflowExecutionStatus.COMPENSATED, WorkflowExecutionStatus.COMPENSATION_FAILED));
        table.put(WorkflowExecutionStatus.COMPLETED, EnumSet.noneOf(WorkflowExecutionStatus.class));
        table.put(WorkflowExecutionStatus.FAILED, EnumSet.noneOf(WorkflowExecutionStatus.class));
        table.put(WorkflowExecutionStatus.COMPENSATED, EnumSet.noneOf(WorkflowExecutionStatus.class));
        table.put(WorkflowExecutionStatus.COMPENSATION_FAILED, EnumSet.noneOf(WorkflowExecutionStatus.class));
        return Map.copyOf(table);
    }

    /**
     * @throws IllegalExecutionTransitionException si la transition n'est pas dans la table
     */
    public void assertTransitionAllowed(WorkflowExecutionStatus from, WorkflowExecutionStatus to) {
        if (from == to) {
            return; // rester dans le meme statut est toujours permis
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalExecutionTransitionException(from, to);
        }
    }

    public boolean isTransitionAllowed(WorkflowExecutionStatus from, WorkflowExecutionStatus to) {
        if (from == to) {
            return true;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
