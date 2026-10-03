package africa.civitas.egen.domain.workflow;

import java.util.Map;

/**
 * Entree opaque d'une execution — voir
 * docs/architecture/11-moteur-de-workflow.md,
 * {@code WorkflowEnginePort.start(WorkflowDefinition, WorkflowInput)}. EGEN
 * ne lit jamais le contenu de {@link #values()} : il est transmis tel quel
 * a la premiere etape, meme garde-fou que {@code TechnicalEvent.data} (voir
 * docs/architecture/05-modele-de-domaine.md).
 */
public record WorkflowInput(Map<String, String> values) {

    public WorkflowInput {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static WorkflowInput empty() {
        return new WorkflowInput(Map.of());
    }
}
