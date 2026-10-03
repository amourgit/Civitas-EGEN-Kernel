package africa.civitas.egen.domain.workflow;

import africa.civitas.egen.domain.model.ServiceId;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Une etape d'une {@link WorkflowDefinition} — voir
 * docs/architecture/11-moteur-de-workflow.md, exemple YAML. Chaque etape
 * reference un service et une operation qu'il expose ; EGEN n'a jamais
 * connaissance de ce que cette operation signifie (meme garde-fou que pour
 * {@link StepOperation}).
 *
 * @param compensation operation inverse a executer si une etape
 *                      ULTERIEURE echoue et que la strategie de compensation
 *                      est {@link CompensationStrategy#BACKWARD} — absente
 *                      pour une etape qui n'a pas d'effet de bord a annuler
 *                      (ex. une etape de lecture/validation pure). Voir
 *                      docs/architecture/11-moteur-de-workflow.md,
 *                      "Compensation — le coeur de la robustesse Saga".
 * @param dependsOn     etapes qui doivent avoir reussi avant que celle-ci ne
 *                      puisse demarrer — jamais une etape de la meme
 *                      definition que celle-ci elle-meme (verifie par
 *                      {@link WorkflowDefinition}, pas ici : la coherence
 *                      globale du graphe n'est verifiable qu'une fois
 *                      toutes les etapes connues).
 */
public record Step(StepId id, ServiceId service, StepOperation operation, List<StepId> dependsOn,
                    Duration timeout, RetryPolicy retry, StepOperation compensation) {

    public Step {
        if (id == null) {
            throw new IllegalArgumentException("Step.id est obligatoire");
        }
        if (service == null) {
            throw new IllegalArgumentException("Step.service est obligatoire");
        }
        if (operation == null) {
            throw new IllegalArgumentException("Step.operation est obligatoire");
        }
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        if (dependsOn.contains(id)) {
            throw new IllegalArgumentException("Step \"" + id + "\" ne peut pas dependre d'elle-meme");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Step.timeout doit etre strictement positif");
        }
        if (retry == null) {
            retry = RetryPolicy.none();
        }
        // compensation reste nullable : voir la javadoc du parametre.
    }

    public Optional<StepOperation> compensationOperation() {
        return Optional.ofNullable(compensation);
    }
}
