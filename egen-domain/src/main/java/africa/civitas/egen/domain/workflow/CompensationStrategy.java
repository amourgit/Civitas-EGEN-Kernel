package africa.civitas.egen.domain.workflow;

/**
 * Voir docs/architecture/11-moteur-de-workflow.md, exemple YAML
 * ({@code compensationStrategy: backward}). Seule {@link #BACKWARD} est
 * documentee ("comportement Saga standard") — aucune autre valeur n'est
 * introduite par anticipation (garde-fou n3,
 * docs/architecture/02-principes-fondamentaux.md).
 */
public enum CompensationStrategy {
    /** Compense dans l'ordre inverse d'execution des etapes deja reussies. */
    BACKWARD
}
