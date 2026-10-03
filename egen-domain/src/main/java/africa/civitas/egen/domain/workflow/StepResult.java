package africa.civitas.egen.domain.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Une entree de la liste append-only portee par {@link WorkflowExecution}
 * (voir docs/architecture/11-moteur-de-workflow.md, "Durabilite de
 * l'execution" et docs/architecture/05-modele-de-domaine.md,
 * {@code List<StepResult>}). Persistee a CHAQUE transition d'etape — c'est
 * ce qui permet au moteur de reprendre une execution exactement ou elle
 * s'etait arretee apres un redemarrage du Kernel.
 *
 * @param attempt      numero de tentative, demarre a 1 (voir
 *                     {@link RetryPolicy#maxAttempts()}) — une nouvelle
 *                     tentative apres echec produit un NOUVEAU StepResult,
 *                     jamais une modification du precedent (discipline
 *                     append-only).
 * @param output       donnees renvoyees par l'operation, opaques pour EGEN
 *                     (jamais interpretees) — vide tant que l'etape n'a pas
 *                     reussi.
 * @param errorMessage absent sauf pour un statut {@link StepStatus#FAILED}
 *                     ou {@link StepStatus#COMPENSATION_FAILED}.
 */
public record StepResult(StepId stepId, StepStatus status, int attempt, Instant startedAt, Instant completedAt,
                          Map<String, String> output, String errorMessage) {

    public StepResult {
        if (stepId == null) {
            throw new IllegalArgumentException("StepResult.stepId est obligatoire");
        }
        if (status == null) {
            throw new IllegalArgumentException("StepResult.status est obligatoire");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("StepResult.attempt doit etre >= 1");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("StepResult.startedAt est obligatoire");
        }
        output = output == null ? Map.of() : Map.copyOf(output);

        boolean terminal = isTerminalStatus(status);
        if (terminal && completedAt == null) {
            throw new IllegalArgumentException(
                    "StepResult.completedAt est obligatoire pour un statut terminal (" + status + ")");
        }
        if (!terminal && completedAt != null) {
            throw new IllegalArgumentException(
                    "StepResult.completedAt doit etre absent pour un statut non terminal (" + status + ")");
        }
        boolean failure = status == StepStatus.FAILED || status == StepStatus.COMPENSATION_FAILED;
        if (failure && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("StepResult.errorMessage est obligatoire pour le statut " + status);
        }
        if (!failure && errorMessage != null) {
            throw new IllegalArgumentException("StepResult.errorMessage doit etre absent pour le statut " + status);
        }
    }

    public Optional<Instant> completion() {
        return Optional.ofNullable(completedAt);
    }

    public Optional<String> error() {
        return Optional.ofNullable(errorMessage);
    }

    public boolean isTerminal() {
        return isTerminalStatus(status);
    }

    private static boolean isTerminalStatus(StepStatus status) {
        return status == StepStatus.SUCCEEDED || status == StepStatus.FAILED
                || status == StepStatus.COMPENSATED || status == StepStatus.COMPENSATION_FAILED;
    }
}
