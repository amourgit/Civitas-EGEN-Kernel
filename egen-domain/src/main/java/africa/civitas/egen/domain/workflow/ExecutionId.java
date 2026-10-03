package africa.civitas.egen.domain.workflow;

import java.util.UUID;

/**
 * Identifiant d'une execution precise d'une {@link WorkflowDefinition} (voir
 * docs/architecture/11-moteur-de-workflow.md, {@code WorkflowEnginePort} —
 * {@code ExecutionId} parametre {@code raiseEvent}/{@code pause}/
 * {@code resume}/{@code terminate}/{@code status}). Un meme
 * {@link WorkflowId} peut avoir de nombreuses {@code ExecutionId}
 * independantes au fil du temps.
 */
public record ExecutionId(String value) {

    public ExecutionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExecutionId ne peut pas etre vide");
        }
    }

    public static ExecutionId generate() {
        return new ExecutionId(UUID.randomUUID().toString());
    }

    public static ExecutionId of(String value) {
        return new ExecutionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
