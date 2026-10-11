package africa.civitas.egen.application.workflow;

import java.time.Duration;

/**
 * Reglages du delai entre tentatives (voir docs/architecture/11-moteur-de-workflow.md,
 * "Retry"). {@code RetryPolicy} ne porte que le nombre de tentatives et la
 * strategie ; le delai de base est un reglage du Kernel, pas du workflow.
 */
public record WorkflowEngineSettings(Duration baseBackoff, Duration maxBackoff) {

    public WorkflowEngineSettings {
        if (baseBackoff == null || baseBackoff.isNegative() || baseBackoff.isZero()) {
            throw new IllegalArgumentException("WorkflowEngineSettings.baseBackoff doit etre strictement positif");
        }
        if (maxBackoff == null || maxBackoff.compareTo(baseBackoff) < 0) {
            throw new IllegalArgumentException("WorkflowEngineSettings.maxBackoff doit etre >= baseBackoff");
        }
    }

    public static WorkflowEngineSettings defaults() {
        return new WorkflowEngineSettings(Duration.ofMillis(500), Duration.ofSeconds(30));
    }
}
