package africa.civitas.egen.domain.workflow;

/**
 * Statut global d'une {@link WorkflowExecution}. Voir
 * docs/architecture/05-modele-de-domaine.md ({@code RUNNING | COMPLETED |
 * COMPENSATING | FAILED | COMPENSATED}) — ETENDU ici de deux valeurs par
 * rapport a cette liste, toutes deux explicitement requises ailleurs dans
 * la documentation et absentes de doc 05 par ce qui semble une omission,
 * pas une exclusion deliberee :
 * <ul>
 *   <li>{@link #COMPENSATION_FAILED} — docs/architecture/11-moteur-de-workflow.md,
 *       "l'execution passe en statut COMPENSATION_FAILED, visible et
 *       alertable, jamais masquee" (texte explicite, non ambigu).</li>
 *   <li>{@link #PAUSED} — necessaire pour que
 *       {@code WorkflowEnginePort.pause(ExecutionId)}
 *       (docs/architecture/11-moteur-de-workflow.md, "Le port") ait un
 *       effet observable via {@code status(ExecutionId)}.</li>
 * </ul>
 */
public enum WorkflowExecutionStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    COMPENSATING,
    FAILED,
    COMPENSATED,
    COMPENSATION_FAILED
}
