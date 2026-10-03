package africa.civitas.egen.domain.workflow;

/**
 * Identifiant d'une {@link Step} au sein d'une {@link WorkflowDefinition}
 * (ex. "validate-content" — voir
 * docs/architecture/11-moteur-de-workflow.md, exemple YAML). Unique
 * seulement DANS une definition donnee, jamais globalement (contrairement a
 * {@link africa.civitas.egen.domain.model.ServiceId}) — validation
 * deliberement plus permissive (pas de convention DNS a respecter, un id
 * d'etape n'est jamais utilise comme nom de ressource externe).
 */
public record StepId(String value) {

    public StepId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("StepId ne peut pas etre vide");
        }
    }

    public static StepId of(String value) {
        return new StepId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
