package africa.civitas.egen.domain.workflow;

/**
 * Statut d'une {@link Step} au sein d'une {@link WorkflowExecution} — voir
 * docs/architecture/11-moteur-de-workflow.md, "Durabilite de l'execution"
 * (chaque transition d'etape est persistee, event-sourcing leger).
 */
public enum StepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    COMPENSATING,
    COMPENSATED,
    /** Voir docs/architecture/11-moteur-de-workflow.md : "visible et alertable, jamais masquee". */
    COMPENSATION_FAILED
}
