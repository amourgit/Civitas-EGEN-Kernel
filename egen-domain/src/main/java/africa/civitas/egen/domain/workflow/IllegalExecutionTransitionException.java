package africa.civitas.egen.domain.workflow;

/**
 * Signale une transition de {@link WorkflowExecutionStatus} non autorisee
 * par {@link WorkflowExecutionStateMachine} — meme discipline que
 * {@link africa.civitas.egen.domain.lifecycle.IllegalPhaseTransitionException}
 * (voir docs/architecture/18-anti-patterns.md : toute transition doit etre
 * rejetee par du code, jamais seulement par convention).
 */
public final class IllegalExecutionTransitionException extends IllegalStateException {

    public IllegalExecutionTransitionException(WorkflowExecutionStatus from, WorkflowExecutionStatus to) {
        super("Transition d'execution interdite : " + from + " -> " + to);
    }
}
